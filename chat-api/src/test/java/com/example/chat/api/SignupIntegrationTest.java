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
        String email = "New_User@Example.COM";
        String rawPassword = "SignupPassword123";

        String response = mockMvc.perform(signup(email, rawPassword, "새 사용자"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.nickname").value("새 사용자"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(userRepository.count()).isEqualTo(1);
        UserEntity saved = userRepository.findByEmail("new_user@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, saved.getPasswordHash())).isTrue();
        assertThat(response).doesNotContain(email, "new_user@example.com", rawPassword, saved.getPasswordHash());
        assertThat(output.getAll()).doesNotContain(email, "new_user@example.com", rawPassword, saved.getPasswordHash());
    }

    @Test
    void duplicateEmailReturnsConflictWithoutCreatingAnotherUser() throws Exception {
        mockMvc.perform(signup("Duplicate_User@Example.COM", "SignupPassword123", "첫 사용자"))
                .andExpect(status().isCreated());

        mockMvc.perform(signup("duplicate_user@example.com", "AnotherPassword456", "두 번째 사용자"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U003"))
                .andExpect(jsonPath("$.message").value("Email already exists"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByEmail("duplicate_user@example.com")).get()
                .extracting(UserEntity::getNickname).isEqualTo("첫 사용자");
    }

    @ParameterizedTest(name = "rejects invalid {3}")
    @MethodSource("invalidSignupRequests")
    void invalidFieldsReturnFieldSpecificBadRequest(
            String email,
            String password,
            String nickname,
            String invalidField
    ) throws Exception {
        mockMvc.perform(signup(email, password, nickname))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.errors." + invalidField).isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(userRepository.count()).isZero();
    }

    static Stream<Arguments> invalidSignupRequests() {
        return Stream.of(
                Arguments.of("", "SignupPassword123", "사용자", "email"),
                Arguments.of("not-an-email", "SignupPassword123", "사용자", "email"),
                Arguments.of("invalid@", "SignupPassword123", "사용자", "email"),
                Arguments.of("a".repeat(245) + "@example.com", "SignupPassword123", "사용자", "email"),
                Arguments.of("valid@example.com", "", "사용자", "password"),
                Arguments.of("valid@example.com", "short1", "사용자", "password"),
                Arguments.of("valid@example.com", "A1" + "x".repeat(71), "사용자", "password"),
                Arguments.of("valid@example.com", "onlyletters", "사용자", "password"),
                Arguments.of("valid@example.com", "SignupPassword123", "", "nickname"),
                Arguments.of("valid@example.com", "SignupPassword123", "A", "nickname"),
                Arguments.of("valid@example.com", "SignupPassword123", "닉".repeat(21), "nickname"),
                Arguments.of("valid@example.com", "SignupPassword123", " 앞뒤공백", "nickname")
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(
            String email,
            String password,
            String nickname
    ) {
        String body = """
                {"email":"%s","password":"%s","nickname":"%s"}
                """.formatted(email, password, nickname);
        return post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
