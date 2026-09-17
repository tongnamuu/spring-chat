package com.example.chat.consumer.service;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import com.example.chat.core.entity.ChatMessageEntity;
import com.example.chat.core.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageConsumerServiceTest {

    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final ChatMessageConsumerService service = new ChatMessageConsumerService(chatMessageRepository);

    @Test
    void reusesExistingMessageWhenKafkaRedeliversSameEventId() {
        ChatMessageDto message = ChatMessageDto.builder()
                .eventId("event-1")
                .roomId(42L)
                .senderId(7L)
                .messageType(MessageType.TALK)
                .content("hello")
                .build();
        ChatMessageEntity existing = ChatMessageEntity.builder()
                .messageId(99L)
                .eventId("event-1")
                .roomId(42L)
                .senderId(7L)
                .messageType(MessageType.TALK)
                .content("hello")
                .build();
        when(chatMessageRepository.findByEventId("event-1")).thenReturn(Optional.of(existing));

        service.consumeMessage(message);

        assertThat(message.getMessageId()).isEqualTo(99L);
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void persistsNewMessageWithEventId() {
        ChatMessageDto message = ChatMessageDto.builder()
                .eventId("event-2")
                .roomId(42L)
                .senderId(7L)
                .messageType(MessageType.TALK)
                .content("hello")
                .build();
        ChatMessageEntity saved = ChatMessageEntity.builder()
                .messageId(100L)
                .eventId("event-2")
                .roomId(42L)
                .senderId(7L)
                .messageType(MessageType.TALK)
                .content("hello")
                .build();
        when(chatMessageRepository.findByEventId("event-2")).thenReturn(Optional.empty());
        when(chatMessageRepository.save(any())).thenReturn(saved);

        service.consumeMessage(message);

        assertThat(message.getMessageId()).isEqualTo(100L);
        verify(chatMessageRepository).save(argThat(entity ->
                "event-2".equals(entity.getEventId())
                        && entity.getRoomId().equals(42L)
                        && entity.getSenderId().equals(7L)
        ));
    }

    @Test
    void reusesExistingMessageWhenUniqueConstraintWinsRace() {
        ChatMessageDto message = ChatMessageDto.builder()
                .eventId("event-3")
                .roomId(42L)
                .senderId(7L)
                .messageType(MessageType.TALK)
                .content("hello")
                .build();
        ChatMessageEntity existing = ChatMessageEntity.builder()
                .messageId(101L)
                .eventId("event-3")
                .roomId(42L)
                .senderId(7L)
                .messageType(MessageType.TALK)
                .content("hello")
                .build();
        when(chatMessageRepository.findByEventId("event-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(chatMessageRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate event"));

        service.consumeMessage(message);

        assertThat(message.getMessageId()).isEqualTo(101L);
    }
}
