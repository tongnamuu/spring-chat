package com.example.chat.api;

import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class SignupIntegrationTest {

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
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void validSignupCreatesOneUserWithHashedPasswordAndSafeResponse(CapturedOutput output) throws Exception {
        String rawPassword = "SignupPassword123";

        String response = mockMvc.perform(signup("new_user@example.com", rawPassword, "새 사용자"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.username").value("new_user@example.com"))
                .andExpect(jsonPath("$.nickname").value("새 사용자"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(userRepository.count()).isEqualTo(1);
        UserEntity saved = userRepository.findByUsername("new_user@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, saved.getPasswordHash())).isTrue();
        assertThat(response).doesNotContain(rawPassword, saved.getPasswordHash());
        assertThat(output.getAll()).doesNotContain(rawPassword, saved.getPasswordHash());
    }

    @Test
    void duplicateUsernameReturnsConflictWithoutCreatingAnotherUser() throws Exception {
        mockMvc.perform(signup("duplicate_user", "SignupPassword123", "첫 사용자"))
                .andExpect(status().isCreated());

        mockMvc.perform(signup("duplicate_user", "AnotherPassword456", "두 번째 사용자"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U003"))
                .andExpect(jsonPath("$.message").value("Username already exists"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByUsername("duplicate_user")).get()
                .extracting(UserEntity::getNickname).isEqualTo("첫 사용자");
    }

    @ParameterizedTest(name = "rejects invalid {3}")
    @MethodSource("invalidSignupRequests")
    void invalidFieldsReturnFieldSpecificBadRequest(
            String username,
            String password,
            String nickname,
            String invalidField
    ) throws Exception {
        mockMvc.perform(signup(username, password, nickname))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.errors." + invalidField).isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(userRepository.count()).isZero();
    }

    static Stream<Arguments> invalidSignupRequests() {
        return Stream.of(
                Arguments.of("", "SignupPassword123", "사용자", "username"),
                Arguments.of("a".repeat(31), "SignupPassword123", "사용자", "username"),
                Arguments.of("a".repeat(51), "SignupPassword123", "사용자", "username"),
                Arguments.of("1invalid", "SignupPassword123", "사용자", "username"),
                Arguments.of("invalid@", "SignupPassword123", "사용자", "username"),
                Arguments.of("valid_user", "", "사용자", "password"),
                Arguments.of("valid_user", "short1", "사용자", "password"),
                Arguments.of("valid_user", "A1" + "x".repeat(71), "사용자", "password"),
                Arguments.of("valid_user", "onlyletters", "사용자", "password"),
                Arguments.of("valid_user", "SignupPassword123", "", "nickname"),
                Arguments.of("valid_user", "SignupPassword123", "A", "nickname"),
                Arguments.of("valid_user", "SignupPassword123", "닉".repeat(21), "nickname"),
                Arguments.of("valid_user", "SignupPassword123", " 앞뒤공백", "nickname")
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(
            String username,
            String password,
            String nickname
    ) {
        String body = """
                {"username":"%s","password":"%s","nickname":"%s"}
                """.formatted(username, password, nickname);
        return post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
