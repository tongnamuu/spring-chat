package com.example.chat.api;

import com.example.chat.api.service.UserAccountService;
import com.example.chat.common.enums.MessageType;
import com.example.chat.common.enums.Role;
import com.example.chat.common.enums.RoomType;
import com.example.chat.core.entity.ChatMessageEntity;
import com.example.chat.core.entity.ChatRoomEntity;
import com.example.chat.core.entity.ChatRoomMemberEntity;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.ChatMessageRepository;
import com.example.chat.core.repository.ChatRoomMemberRepository;
import com.example.chat.core.repository.ChatRoomRepository;
import com.example.chat.core.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RoomKickIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.show-sql", () -> false);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ChatRoomRepository chatRoomRepository;

    @Autowired
    ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    ChatMessageRepository chatMessageRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;

    @Autowired
    com.example.chat.core.service.ChatRoomService rooms;
    @Autowired
    com.example.chat.core.repository.ChatRoomBanRepository bans;

    private UserEntity alice;
    private UserEntity bob;
    private UserEntity charlie;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        bans.deleteAll();
        chatMessageRepository.deleteAll();
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
        userRepository.deleteAll();
        alice = saveUser("alice", "Alice", "alice-password");
        bob = saveUser("bob", "Bob", "bob-password");
        charlie = saveUser("charlie", "Charlie", "charlie-password");
    }

    @Test
    void kickRevokesHistorySyncMembersAndRejoinButPreservesOtherRoomsAndHistory() throws Exception {
        var room = sharedRoom("kicked");
        var other = sharedRoom("other");
        chatMessageRepository.save(ChatMessageEntity.builder().roomId(room.getRoomId())
                .senderId(bob.getUserId()).messageType(MessageType.TALK).content("retained").build());
        Cookie owner = login("alice", "alice-password");
        Cookie target = login("bob", "bob-password");
        mockMvc.perform(get("/api/rooms/{id}/members", room.getRoomId()).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(delete("/api/rooms/{id}/members/{user}", room.getRoomId(), bob.getUserId()).cookie(owner))
                .andExpect(status().isNoContent());
        for (String suffix : List.of("/messages", "/sync?lastReceivedMessageId=0", "/members")) {
            mockMvc.perform(get("/api/rooms/" + room.getRoomId() + suffix).cookie(target))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("R004"));
        }
        mockMvc.perform(post("/api/rooms/join").cookie(target).contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\":\"" + room.getInviteCode() + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("R010"));
        mockMvc.perform(get("/api/rooms/my").cookie(target)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].roomId").value(other.getRoomId()));
        mockMvc.perform(get("/api/rooms/{id}/messages", room.getRoomId()).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].content").value("retained"));
        mockMvc.perform(get("/api/rooms/{id}/messages", other.getRoomId()).cookie(target)).andExpect(status().isOk());
        assertThat(chatRoomRepository.findById(room.getRoomId()).orElseThrow().getCurrentCount()).isEqualTo(1);
        assertThat(bans.existsByRoomIdAndUserId(room.getRoomId(), bob.getUserId())).isTrue();
    }

    @Test
    void onlyOwnerCanKickAndCannotKickSelfOrUnknownMember() throws Exception {
        var room = sharedRoom("authorization");
        Cookie owner = login("alice", "alice-password");
        Cookie member = login("bob", "bob-password");
        Cookie outsider = login("charlie", "charlie-password");
        String url = "/api/rooms/" + room.getRoomId() + "/members/";
        mockMvc.perform(delete(url + bob.getUserId())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(url + alice.getUserId()).cookie(member)).andExpect(status().isForbidden());
        mockMvc.perform(delete(url + bob.getUserId()).cookie(outsider)).andExpect(status().isForbidden());
        mockMvc.perform(delete(url + alice.getUserId()).cookie(owner)).andExpect(status().isBadRequest());
        mockMvc.perform(delete(url + charlie.getUserId()).cookie(owner)).andExpect(status().isForbidden());
        assertThat(chatRoomRepository.findById(room.getRoomId()).orElseThrow().getCurrentCount()).isEqualTo(2);
        assertThat(bans.count()).isZero();
    }

    @Test
    void concurrentKicksDecrementCountExactlyOnceAndCannotBeUndoneByJoin() throws Exception {
        var room = sharedRoom("concurrent-kick");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var first = pool.submit(() -> { start.await(); rooms.kickMember(alice.getUserId(), room.getRoomId(), bob.getUserId()); return true; });
            var second = pool.submit(() -> { start.await(); rooms.kickMember(alice.getUserId(), room.getRoomId(), bob.getUserId()); return true; });
            var join = pool.submit(() -> {
                start.await();
                try { rooms.joinRoomByInviteCode(bob.getUserId(), room.getInviteCode()); }
                catch (com.example.chat.common.exception.ChatException expected) {
                    assertThat(expected.getErrorCode()).isIn(com.example.chat.common.exception.ErrorCode.ALREADY_JOINED,
                            com.example.chat.common.exception.ErrorCode.ROOM_BANNED);
                }
                return true;
            });
            start.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(join.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        assertThat(chatRoomRepository.findById(room.getRoomId()).orElseThrow().getCurrentCount()).isEqualTo(1);
        assertThat(bans.count()).isEqualTo(1);
        assertThat(chatRoomMemberRepository.existsByRoomIdAndUserId(room.getRoomId(), bob.getUserId())).isFalse();
    }

    @Test
    void withdrawnOwnerSuccessorCanKickAndBannedUsersAreNotPromoted() {
        var room = sharedRoom("successor");
        saveMember(room, charlie, Role.MEMBER);
        room.setCurrentCount(3);
        chatRoomRepository.save(room);
        rooms.kickMember(alice.getUserId(), room.getRoomId(), bob.getUserId());
        userAccountService.withdraw(alice.getUserId(), "alice-password");
        var remaining = chatRoomRepository.findById(room.getRoomId()).orElseThrow();
        assertThat(remaining.getCreatedBy()).isEqualTo(charlie.getUserId());
        assertThat(remaining.getCurrentCount()).isEqualTo(1);
        rooms.kickMember(charlie.getUserId(), room.getRoomId(), bob.getUserId());
        assertThat(rooms.getMembers(charlie.getUserId(), room.getRoomId())).hasSize(1);
    }

    @Test
    void notificationIsPublishedOnlyAfterCommitAndNeverAfterRollback() throws Exception {
        var room = sharedRoom("commit-only");
        var notifications = new java.util.concurrent.LinkedBlockingQueue<String>();
        var listener = new org.springframework.data.redis.listener.RedisMessageListenerContainer();
        listener.setConnectionFactory(redisTemplate.getConnectionFactory());
        listener.addMessageListener((message, pattern) -> notifications.add(message.toString()),
                new org.springframework.data.redis.listener.ChannelTopic(com.example.chat.common.dto.RoomMemberKicked.CHANNEL));
        listener.afterPropertiesSet();
        listener.start();
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                rooms.kickMember(alice.getUserId(), room.getRoomId(), bob.getUserId());
                assertThat(notifications).isEmpty();
                status.setRollbackOnly();
            });
            assertThat(notifications.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS)).isNull();
            assertThat(chatRoomMemberRepository.existsByRoomIdAndUserId(room.getRoomId(), bob.getUserId())).isTrue();
            assertThat(bans.count()).isZero();
            rooms.kickMember(alice.getUserId(), room.getRoomId(), bob.getUserId());
            assertThat(notifications.poll(5, java.util.concurrent.TimeUnit.SECONDS))
                    .contains("\"roomId\":" + room.getRoomId(), "\"userId\":" + bob.getUserId());
        } finally {
            listener.destroy();
        }
    }

    private ChatRoomEntity sharedRoom(String title) {
        var room = saveRoom(title, alice.getUserId(), 2);
        saveMember(room, alice, Role.OWNER);
        saveMember(room, bob, Role.MEMBER);
        return room;
    }

    private Cookie login(String username, String password) throws Exception {
        MvcResult result = loginRequest(username, password).andExpect(status().isOk()).andReturn();
        return Objects.requireNonNull(result.getResponse().getCookie("CHAT_SESSION"));
    }

    private org.springframework.test.web.servlet.ResultActions loginRequest(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private UserEntity saveUser(String username, String nickname, String password) {
        return userRepository.save(UserEntity.builder()
                .username(username)
                .nickname(nickname)
                .passwordHash(passwordEncoder.encode(password))
                .build());
    }

    private ChatRoomEntity saveRoom(String title, Long ownerId, int currentCount) {
        return chatRoomRepository.save(ChatRoomEntity.builder()
                .title(title)
                .roomType(RoomType.GROUP_PUBLIC)
                .inviteCode(java.util.UUID.randomUUID().toString().substring(0, 8))
                .maxCapacity(10)
                .currentCount(currentCount)
                .createdBy(ownerId)
                .build());
    }

    private ChatRoomMemberEntity saveMember(ChatRoomEntity room, UserEntity user, Role role) {
        return chatRoomMemberRepository.save(ChatRoomMemberEntity.builder()
                .roomId(room.getRoomId())
                .userId(user.getUserId())
                .role(role)
                .build());
    }
}
