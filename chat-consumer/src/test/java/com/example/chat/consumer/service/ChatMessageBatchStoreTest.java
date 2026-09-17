package com.example.chat.consumer.service;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.kafka.listener.auto-startup=false", "spring.jpa.hibernate.ddl-auto=create"})
@Testcontainers
class ChatMessageBatchStoreTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withUrlParam("reWriteBatchedInserts", "true");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired ChatMessageBatchStore store;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM chat_message");
    }

    @Test
    void persistsBatchAndReusesIdsOnReplayWithoutOverwritingOriginalContent() {
        List<ChatMessageDto> messages = IntStream.range(0, 500).mapToObj(this::message).toList();
        store.persist(messages);
        List<Long> ids = messages.stream().map(ChatMessageDto::getMessageId).toList();
        messages.getFirst().setContent("changed retry");
        store.persist(messages);
        assertThat(messages.stream().map(ChatMessageDto::getMessageId)).containsExactlyElementsOf(ids);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message", Long.class)).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT content FROM chat_message WHERE event_id = 'event-0'", String.class))
                .isEqualTo("message-0");
        assertThat(store.previousMessageId(42L, ids.get(100))).isEqualTo(ids.get(99));
        assertThat(ids).isSorted();
    }

    @Test
    void invalidRecordRollsBackEntireBatch() {
        ChatMessageDto invalid = message(2);
        invalid.setContent(null);
        assertThatThrownBy(() -> store.persist(List.of(message(1), invalid))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message", Long.class)).isZero();
    }

    private ChatMessageDto message(int i) {
        return ChatMessageDto.builder().eventId("event-" + i).roomId(42L).senderId(7L)
                .messageType(MessageType.TALK).content("message-" + i).createdAt(ZonedDateTime.now()).build();
    }
}
