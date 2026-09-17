package com.example.chat.core.service;

import com.example.chat.common.dto.CreateRoomRequest;
import com.example.chat.common.enums.RoomType;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.ChatRoomRepository;
import com.example.chat.core.repository.ChatRoomMemberRepository;
import com.example.chat.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.example.chat.core.TestCoreApplication.class)
@Testcontainers
@ActiveProfiles("test")
class ConcurrentRoomJoinTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired ChatRoomService service;
    @Autowired UserRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatRoomMemberRepository members;

    @Test
    void concurrentJoinsDoNotLoseCountsOrFailOptimisticLocking() {
        var participants = IntStream.range(0, 21).mapToObj(i -> users.save(UserEntity.builder()
                .username("join-user-" + i).nickname("User " + i).passwordHash("hash").build())).toList();
        var room = service.createRoom(participants.getFirst().getUserId(), CreateRoomRequest.builder()
                .title("Concurrent room").roomType(RoomType.GROUP_PUBLIC).maxCapacity(1500).build());
        try (var executor = Executors.newFixedThreadPool(10)) {
            var tasks = participants.subList(1, participants.size()).stream().map(user ->
                    CompletableFuture.runAsync(() -> service.joinRoomByInviteCode(user.getUserId(), room.getInviteCode()), executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(tasks).join();
        }
        assertThat(rooms.findById(room.getRoomId()).orElseThrow().getCurrentCount()).isEqualTo(21);
        assertThat(members.findByRoomId(room.getRoomId())).hasSize(21);
    }
}
