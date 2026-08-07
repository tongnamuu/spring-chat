package com.example.chat.consumer.service;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.core.entity.ChatMessageEntity;
import com.example.chat.core.repository.ChatMessageRepository;
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

        ChatMessageEntity entity = ChatMessageEntity.builder()
                .roomId(messageDto.getRoomId())
                .senderId(messageDto.getSenderId())
                .messageType(messageDto.getMessageType())
                .content(messageDto.getContent())
                .build();

        ChatMessageEntity saved = chatMessageRepository.save(entity);
        messageDto.setMessageId(saved.getMessageId());
    }
}
