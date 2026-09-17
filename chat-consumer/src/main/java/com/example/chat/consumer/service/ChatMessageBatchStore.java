package com.example.chat.consumer.service;

import com.example.chat.common.dto.ChatMessageDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatMessageBatchStore {
    private final JdbcTemplate jdbc;

    public ChatMessageBatchStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long previousMessageId(Long roomId, Long firstMessageId) {
        Long previous = jdbc.queryForObject(
                "SELECT COALESCE(MAX(message_id), 0) FROM chat_message WHERE room_id = ? AND message_id < ?",
                Long.class, roomId, firstMessageId);
        return previous == null ? 0 : previous;
    }

    @Transactional
    public void persist(List<ChatMessageDto> messages) {
        if (messages.isEmpty()) return;
        // Duplicate events must not abort the enclosing PostgreSQL transaction.
        jdbc.batchUpdate("""
                INSERT INTO chat_message (event_id, room_id, sender_id, message_type, content, created_at)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, messages, 500, (ps, message) -> {
            if (message.getEventId() == null || message.getEventId().isBlank()) {
                throw new IllegalArgumentException("eventId is required");
            }
            ps.setString(1, message.getEventId());
            ps.setLong(2, message.getRoomId());
            ps.setLong(3, message.getSenderId());
            ps.setString(4, message.getMessageType().name());
            ps.setString(5, message.getContent());
            ps.setObject(6, (message.getCreatedAt() == null ? ZonedDateTime.now() : message.getCreatedAt()).toOffsetDateTime());
        });
        Map<String, Long> ids = new HashMap<>();
        String placeholders = String.join(",", Collections.nCopies(messages.size(), "?"));
        jdbc.query("SELECT event_id, message_id FROM chat_message WHERE event_id IN (" + placeholders + ")",
                rs -> { ids.put(rs.getString(1), rs.getLong(2)); },
                messages.stream().map(ChatMessageDto::getEventId).toArray());
        for (ChatMessageDto message : messages) {
            Long id = ids.get(message.getEventId());
            if (id == null) throw new IllegalStateException("Persisted message ID missing");
            message.setMessageId(id);
        }
    }
}
