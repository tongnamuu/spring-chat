package com.example.chat.core.service;

import com.example.chat.common.dto.ChatRoomDto;
import com.example.chat.common.dto.CreateRoomRequest;
import com.example.chat.common.enums.RoomType;
import com.example.chat.common.exception.ChatException;
import com.example.chat.common.exception.ErrorCode;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.ChatRoomMemberRepository;
import com.example.chat.core.repository.ChatRoomRepository;
import com.example.chat.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = com.example.chat.core.TestCoreApplication.class)
@Testcontainers
@Transactional
@ActiveProfiles("test")
class ChatRoomServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    private UserEntity user1;
    private UserEntity user2;

    @BeforeEach
    void setUp() {
        user1 = userRepository.save(UserEntity.builder().username("user1").nickname("User One").build());
        user2 = userRepository.save(UserEntity.builder().username("user2").nickname("User Two").build());
    }

    @Test
    @DisplayName("실제 PostgreSQL Testcontainers - 최대 50명 방 개설 성공 테스트")
    void createRoom_Success() {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .title("테스트 퍼블릭 방")
                .roomType(RoomType.GROUP_PUBLIC)
                .maxCapacity(50)
                .build();

        ChatRoomDto room = chatRoomService.createRoom(user1.getUserId(), request);

        assertThat(room).isNotNull();
        assertThat(room.getTitle()).isEqualTo("테스트 퍼블릭 방");
        assertThat(room.getMaxCapacity()).isEqualTo(50);
        assertThat(room.getCurrentCount()).isEqualTo(1);
        assertThat(room.getInviteCode()).isNotNull().hasSize(8);
    }

    @Test
    @DisplayName("실제 PostgreSQL Testcontainers - 1대1 채팅방 개설 시 최대 인원 2명 자동 지정")
    void createDirectRoom_Success() {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .title("1:1 대화")
                .roomType(RoomType.DIRECT)
                .targetUserId(user2.getUserId())
                .build();

        ChatRoomDto room = chatRoomService.createRoom(user1.getUserId(), request);

        assertThat(room.getRoomType()).isEqualTo(RoomType.DIRECT);
        assertThat(room.getMaxCapacity()).isEqualTo(2);
        assertThat(room.getCurrentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("실제 PostgreSQL Testcontainers - 초대 코드로 공개/비공개 방 입장 성공")
    void joinRoomByInviteCode_Success() {
        CreateRoomRequest createReq = CreateRoomRequest.builder()
                .title("프라이빗 방")
                .roomType(RoomType.GROUP_PRIVATE)
                .maxCapacity(10)
                .build();

        ChatRoomDto createdRoom = chatRoomService.createRoom(user1.getUserId(), createReq);

        ChatRoomDto joinedRoom = chatRoomService.joinRoomByInviteCode(user2.getUserId(), createdRoom.getInviteCode());

        assertThat(joinedRoom.getCurrentCount()).isEqualTo(2);
        assertThat(chatRoomMemberRepository.existsByRoomIdAndUserId(createdRoom.getRoomId(), user2.getUserId())).isTrue();
    }

    @Test
    @DisplayName("실제 PostgreSQL Testcontainers - 최대 인원 초과 시 입장 실패 예외 발생")
    void joinRoom_FullCapacity_ThrowsException() {
        CreateRoomRequest createReq = CreateRoomRequest.builder()
                .title("소규모 방")
                .roomType(RoomType.GROUP_PUBLIC)
                .maxCapacity(2)
                .build();

        ChatRoomDto createdRoom = chatRoomService.createRoom(user1.getUserId(), createReq);
        chatRoomService.joinRoomByInviteCode(user2.getUserId(), createdRoom.getInviteCode());

        UserEntity user3 = userRepository.save(UserEntity.builder().username("user3").nickname("User Three").build());

        assertThatThrownBy(() -> chatRoomService.joinRoomByInviteCode(user3.getUserId(), createdRoom.getInviteCode()))
                .isInstanceOf(ChatException.class)
                .hasMessageContaining(ErrorCode.ROOM_FULL.getMessage());
    }

    @Test
    @DisplayName("실제 PostgreSQL Testcontainers - 본인이 참여한 채팅방만 목록 조회")
    void getMyJoinedRooms_Success() {
        CreateRoomRequest req1 = CreateRoomRequest.builder().title("Room 1").roomType(RoomType.GROUP_PUBLIC).maxCapacity(50).build();
        CreateRoomRequest req2 = CreateRoomRequest.builder().title("Room 2").roomType(RoomType.GROUP_PUBLIC).maxCapacity(50).build();

        ChatRoomDto room1 = chatRoomService.createRoom(user1.getUserId(), req1);
        chatRoomService.createRoom(user2.getUserId(), req2);

        List<ChatRoomDto> myRoomsUser1 = chatRoomService.getMyJoinedRooms(user1.getUserId());
        List<ChatRoomDto> myRoomsUser2 = chatRoomService.getMyJoinedRooms(user2.getUserId());

        assertThat(myRoomsUser1).hasSize(1);
        assertThat(myRoomsUser1.get(0).getRoomId()).isEqualTo(room1.getRoomId());
        assertThat(myRoomsUser2).hasSize(1);
    }
}
