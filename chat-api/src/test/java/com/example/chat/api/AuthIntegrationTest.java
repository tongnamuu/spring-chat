package com.example.chat.api;

import com.example.chat.core.entity.UserEntity;
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

import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

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
    PasswordEncoder passwordEncoder;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    SessionRepository<? extends Session> sessionRepository;

    private UserEntity alice;
    private UserEntity bob;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        userRepository.deleteAll();
        alice = saveUser("alice", "Alice", "alice-password");
        bob = saveUser("bob", "Bob", "bob-password");
    }

    @Test
    void loginCreatesRedisSessionAndMeReturnsAuthenticatedUser() throws Exception {
        MvcResult login = login("alice", "alice-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(alice.getUserId()))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.nickname").value("Alice"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        Cookie sessionCookie = requireSessionCookie(login);
        String setCookie = Objects.requireNonNull(login.getResponse().getHeader("Set-Cookie"));
        assertThat(setCookie).contains("HttpOnly", "SameSite=Lax").doesNotContain("alice-password");
        assertThat(login.getResponse().getContentAsString()).doesNotContain(sessionCookie.getValue());
        assertThat(redisSessionKeys()).hasSize(1);

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(alice.getUserId()))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void invalidUsernameAndPasswordReturnTheSameUnauthorizedResponseWithoutSession() throws Exception {
        String wrongPassword = login("alice", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andReturn().getResponse().getContentAsString();

        String unknownUser = login("missing", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownUser).isEqualTo(wrongPassword);
        assertThat(redisSessionKeys()).isEmpty();
    }

    @Test
    void protectedRestApiUsesPrincipalAndIgnoresForgedUserHeader() throws Exception {
        Cookie aliceSession = requireSessionCookie(login("alice", "alice-password").andReturn());

        mockMvc.perform(post("/api/rooms")
                        .cookie(aliceSession)
                        .header("X-User-Id", bob.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Alice room","roomType":"GROUP_PUBLIC","maxCapacity":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value(alice.getUserId()));

        assertThat(chatRoomRepository.findAll()).singleElement()
                .extracting(room -> room.getCreatedBy()).isEqualTo(alice.getUserId());
    }

    @Test
    void unauthenticatedRequestAndLoggedOutSessionCannotUseProtectedApi() throws Exception {
        mockMvc.perform(get("/api/rooms/my").header("X-User-Id", alice.getUserId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        Cookie session = requireSessionCookie(login("alice", "alice-password").andReturn());
        assertThat(redisSessionKeys()).hasSize(1);

        mockMvc.perform(post("/api/auth/logout").cookie(session))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
        assertThat(redisSessionKeys()).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private Cookie requireSessionCookie(MvcResult result) {
        return Objects.requireNonNull(result.getResponse().getCookie("CHAT_SESSION"));
    }

    private Set<String> redisSessionKeys() {
        Set<String> keys = redisTemplate.keys("spring-chat:session:sessions:*");
        return keys == null ? Set.of() : keys.stream()
                .filter(key -> !key.contains(":expires:"))
                .filter(key -> sessionRepository.findById(key.substring(key.lastIndexOf(':') + 1)) != null)
                .collect(java.util.stream.Collectors.toSet());
    }

    private UserEntity saveUser(String username, String nickname, String password) {
        return userRepository.save(UserEntity.builder()
                .username(username)
                .nickname(nickname)
                .passwordHash(passwordEncoder.encode(password))
                .build());
    }
}
