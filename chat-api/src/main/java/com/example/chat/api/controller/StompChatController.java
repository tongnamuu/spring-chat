package com.example.chat.api.controller;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.time.ZonedDateTime;

@Controller
public class StompChatController {

    private static final Logger log = LoggerFactory.getLogger(StompChatController.class);
    private static final String CHAT_MESSAGES_TOPIC = "chat-messages";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimpMessageSendingOperations messagingTemplate;

    public StompChatController(KafkaTemplate<String, Object> kafkaTemplate, SimpMessageSendingOperations messagingTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/message")
    public void message(ChatMessageDto message) {
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(ZonedDateTime.now());
        }

        if (MessageType.ENTER.equals(message.getMessageType())) {
            message.setContent(message.getSenderName() + "님이 입장하셨습니다.");
        } else if (MessageType.LEAVE.equals(message.getMessageType())) {
            message.setContent(message.getSenderName() + "님이 퇴장하셨습니다.");
        }

        log.info("Received STOMP message: roomId={}, type={}, sender={}, content={}", 
                message.getRoomId(), message.getMessageType(), message.getSenderName(), message.getContent());

        // 1. Send to Kafka
        try {
            kafkaTemplate.send(CHAT_MESSAGES_TOPIC, String.valueOf(message.getRoomId()), message);
        } catch (Exception e) {
            log.warn("Kafka produce error, proceeding with WS broadcast: {}", e.getMessage());
        }

        // 2. Broadcast immediately to WS subscribers of /sub/chat/room/{roomId}
        messagingTemplate.convertAndSend("/sub/chat/room/" + message.getRoomId(), message);
    }
}
