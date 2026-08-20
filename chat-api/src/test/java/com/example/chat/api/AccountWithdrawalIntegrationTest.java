package com.example.chat.api;

import com.example.chat.api.service.UserAccountService;
import com.example.chat.common.enums.MessageType;
import com.example.chat.common.enums.Role;
import com.example.chat.common.enums.RoomType;
import com.example.chat.common.enums.UserStatus;
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
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
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
import java.util.Set;
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
class AccountWithdrawalIntegrationTest {

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
    SessionRepository<? extends Session> sessionRepository;

    private UserEntity alice;
    private UserEntity bob;
    private UserEntity charlie;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        chatMessageRepository.deleteAll();
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
        userRepository.deleteAll();
        alice = saveUser("alice@example.com", "Alice", "alice-password");
        bob = saveUser("bob@example.com", "Bob", "bob-password");
        charlie = saveUser("charlie@example.com", "Charlie", "charlie-password");
    }

    @Test
    void withdrawalAnonymizesUserRepairsRoomsPreservesMessagesAndInvalidatesEverySession() throws Exception {
        ChatRoomEntity sharedRoom = saveRoom("shared", alice.getUserId(), 3);
        saveMember(sharedRoom, alice, Role.OWNER);
        saveMember(sharedRoom, bob, Role.MEMBER);
        saveMember(sharedRoom, charlie, Role.MEMBER);

        ChatRoomEntity emptyRoom = saveRoom("empty", alice.getUserId(), 1);
        saveMember(emptyRoom, alice, Role.OWNER);

        ChatRoomEntity joinedRoom = saveRoom("joined", bob.getUserId(), 2);
        saveMember(joinedRoom, bob, Role.OWNER);
        saveMember(joinedRoom, alice, Role.MEMBER);

        chatMessageRepository.save(ChatMessageEntity.builder()
                .roomId(sharedRoom.getRoomId())
                .senderId(alice.getUserId())
                .messageType(MessageType.TALK)
                .content("hello")
                .build());
        chatMessageRepository.save(ChatMessageEntity.builder()
                .roomId(sharedRoom.getRoomId())
                .senderId(alice.getUserId())
                .messageType(MessageType.ENTER)
                .content("Alice님이 입장하셨습니다.")
                .build());
        ChatMessageEntity retainedEmptyRoomMessage = chatMessageRepository.save(ChatMessageEntity.builder()
                .roomId(emptyRoom.getRoomId())
                .senderId(alice.getUserId())
                .messageType(MessageType.TALK)
                .content("retained history")
                .build());

        Cookie firstSession = login("alice@example.com", "alice-password");
        Cookie secondSession = login("alice@example.com", "alice-password");
        assertThat(redisSessionKeys()).hasSize(2);

        mockMvc.perform(delete("/api/users/me")
                        .cookie(firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"alice-password\"}"))
                .andExpect(status().isNoContent());

        UserEntity withdrawn = userRepository.findById(alice.getUserId()).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawn.getDeletedAt()).isNotNull();
        assertThat(withdrawn.getEmail()).isEqualTo("withdrawn-" + alice.getUserId() + "@deleted.invalid");
        assertThat(withdrawn.getNickname()).isEqualTo(UserEntity.WITHDRAWN_NICKNAME);
        assertThat(passwordEncoder.matches("alice-password", withdrawn.getPasswordHash())).isFalse();

        assertThat(chatRoomMemberRepository.findByUserIdOrderByRoomIdAsc(alice.getUserId())).isEmpty();
        ChatRoomEntity repairedSharedRoom = chatRoomRepository.findById(sharedRoom.getRoomId()).orElseThrow();
        assertThat(repairedSharedRoom.getCurrentCount()).isEqualTo(2);
        assertThat(repairedSharedRoom.getCreatedBy()).isEqualTo(bob.getUserId());
        assertThat(chatRoomMemberRepository.findByRoomIdAndUserId(sharedRoom.getRoomId(), bob.getUserId()))
                .get().extracting(ChatRoomMemberEntity::getRole).isEqualTo(Role.OWNER);
        assertThat(chatRoomRepository.findById(emptyRoom.getRoomId())).isEmpty();
        assertThat(chatRoomRepository.findById(joinedRoom.getRoomId())).get()
                .extracting(ChatRoomEntity::getCurrentCount).isEqualTo(1);
        assertThat(chatMessageRepository.findAll()).hasSize(3);
        assertThat(chatMessageRepository.findById(retainedEmptyRoomMessage.getMessageId())).isPresent();

        mockMvc.perform(get("/api/auth/me").cookie(firstSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/me")
                        .cookie(secondSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"alice-password\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(redisSessionKeys()).isEmpty();
        assertThat(chatRoomRepository.findById(sharedRoom.getRoomId())).get()
                .extracting(ChatRoomEntity::getCurrentCount).isEqualTo(2);
        loginRequest("alice@example.com", "alice-password").andExpect(status().isUnauthorized());

        Cookie bobSession = login("bob@example.com", "bob-password");
        mockMvc.perform(get("/api/users/{userId}", alice.getUserId()).cookie(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.nickname").value(UserEntity.WITHDRAWN_NICKNAME));
        mockMvc.perform(get("/api/rooms/{roomId}/messages", sharedRoom.getRoomId()).cookie(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senderName").value(UserEntity.WITHDRAWN_NICKNAME))
                .andExpect(jsonPath("$[0].content").value(UserEntity.WITHDRAWN_NICKNAME + "님이 입장하셨습니다."))
                .andExpect(jsonPath("$[1].senderName").value(UserEntity.WITHDRAWN_NICKNAME))
                .andExpect(jsonPath("$[1].content").value("hello"));
    }

    @Test
    void invalidPasswordAndUnauthenticatedRequestDoNotChangeAccountMembershipOrSession() throws Exception {
        ChatRoomEntity room = saveRoom("protected", alice.getUserId(), 2);
        saveMember(room, alice, Role.OWNER);
        saveMember(room, bob, Role.MEMBER);
        Cookie session = login("alice@example.com", "alice-password");

        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"alice-password\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/me")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U002"));

        UserEntity unchanged = userRepository.findById(alice.getUserId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(unchanged.getEmail()).isEqualTo("alice@example.com");
        assertThat(unchanged.getDeletedAt()).isNull();
        assertThat(chatRoomMemberRepository.existsByRoomIdAndUserId(room.getRoomId(), alice.getUserId())).isTrue();
        assertThat(chatRoomRepository.findById(room.getRoomId())).get()
                .extracting(ChatRoomEntity::getCurrentCount).isEqualTo(2);
        assertThat(redisSessionKeys()).hasSize(1);
        mockMvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void concurrentWithdrawalChangesMembershipAndCountExactlyOnce() throws Exception {
        ChatRoomEntity room = saveRoom("concurrent", alice.getUserId(), 2);
        saveMember(room, alice, Role.OWNER);
        saveMember(room, bob, Role.MEMBER);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return userAccountService.withdraw(alice.getUserId(), "alice-password");
            });
            var second = executor.submit(() -> {
                start.await();
                return userAccountService.withdraw(alice.getUserId(), "alice-password");
            });
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }

        assertThat(chatRoomMemberRepository.findByUserIdOrderByRoomIdAsc(alice.getUserId())).isEmpty();
        assertThat(chatRoomRepository.findById(room.getRoomId())).get()
                .satisfies(repaired -> {
                    assertThat(repaired.getCurrentCount()).isEqualTo(1);
                    assertThat(repaired.getCreatedBy()).isEqualTo(bob.getUserId());
                });
        assertThat(chatRoomMemberRepository.findByRoomIdAndUserId(room.getRoomId(), bob.getUserId()))
                .get().extracting(ChatRoomMemberEntity::getRole).isEqualTo(Role.OWNER);
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult result = loginRequest(email, password).andExpect(status().isOk()).andReturn();
        return Objects.requireNonNull(result.getResponse().getCookie("CHAT_SESSION"));
    }

    private org.springframework.test.web.servlet.ResultActions loginRequest(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private Set<String> redisSessionKeys() {
        Set<String> keys = redisTemplate.keys("spring-chat:session:sessions:*");
        return keys == null ? Set.of() : keys.stream()
                .filter(key -> !key.contains(":expires:"))
                .filter(key -> sessionRepository.findById(key.substring(key.lastIndexOf(':') + 1)) != null)
                .collect(java.util.stream.Collectors.toSet());
    }

    private UserEntity saveUser(String email, String nickname, String password) {
        return userRepository.save(UserEntity.builder()
                .email(email)
                .nickname(nickname)
                .passwordHash(passwordEncoder.encode(password))
                .build());
    }

    private ChatRoomEntity saveRoom(String title, Long ownerId, int currentCount) {
        return chatRoomRepository.save(ChatRoomEntity.builder()
                .title(title)
                .roomType(RoomType.GROUP_PUBLIC)
                .inviteCode(title + "-code")
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
