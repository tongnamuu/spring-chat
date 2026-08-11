package com.example.chat.ws.config;

import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.UserRepository;
import com.example.chat.core.security.ChatPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SpringBootTest
@Testcontainers
class StompWithdrawalIntegrationTest {

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
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    UserRepository userRepository;

    @Autowired
    StompAuthenticationChannelInterceptor interceptor;

    private UserEntity user;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        user = userRepository.save(UserEntity.builder()
                .email("alice@example.com")
                .nickname("Alice")
                .passwordHash("password-hash")
                .build());
        ChatPrincipal principal = new ChatPrincipal(user.getUserId());
        authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of());
    }

    @Test
    void rejectsFurtherStompFramesAfterUserWithdrawal() {
        Message<byte[]> send = message(StompCommand.SEND, authentication);
        assertThat(interceptor.preSend(send, mock())).isSameAs(send);

        user.withdraw("invalidated-hash", ZonedDateTime.now());
        userRepository.saveAndFlush(user);

        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SEND, authentication), mock()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SUBSCRIBE, authentication), mock()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> message(StompCommand command, java.security.Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
