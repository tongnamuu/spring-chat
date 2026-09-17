package com.example.chat.consumer.service;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.core.entity.ChatMessageEntity;
import com.example.chat.core.repository.ChatMessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessageConsumerService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageConsumerService.class);

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageConsumerService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    @KafkaListener(topics = "chat-messages", groupId = "chat-db-persistence-group")
    public void consumeMessage(ChatMessageDto messageDto) {
        log.info("Kafka Consumer persisting message to Postgres DB: roomId={}, senderId={}, type={}",
                messageDto.getRoomId(), messageDto.getSenderId(), messageDto.getMessageType());

        if (messageDto.getEventId() != null) {
            ChatMessageEntity existing = chatMessageRepository.findByEventId(messageDto.getEventId()).orElse(null);
            if (existing != null) {
                messageDto.setMessageId(existing.getMessageId());
                return;
            }
        }

        ChatMessageEntity entity = ChatMessageEntity.builder()
                .eventId(messageDto.getEventId())
                .roomId(messageDto.getRoomId())
                .senderId(messageDto.getSenderId())
                .messageType(messageDto.getMessageType())
                .content(messageDto.getContent())
                .createdAt(messageDto.getCreatedAt())
                .build();

        ChatMessageEntity saved = saveIdempotently(entity, messageDto.getEventId());
        messageDto.setMessageId(saved.getMessageId());
    }

    private ChatMessageEntity saveIdempotently(ChatMessageEntity entity, String eventId) {
        try {
            return chatMessageRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            if (eventId == null) {
                throw ex;
            }
            return chatMessageRepository.findByEventId(eventId).orElseThrow(() -> ex);
        }
    }
}
