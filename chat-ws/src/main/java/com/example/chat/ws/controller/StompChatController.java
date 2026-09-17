package com.example.chat.ws.controller;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import com.example.chat.core.security.ChatPrincipal;
import com.example.chat.ws.producer.KafkaMessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.time.ZonedDateTime;
import java.security.Principal;
import java.util.UUID;

@Controller
public class StompChatController {

    private static final Logger log = LoggerFactory.getLogger(StompChatController.class);

    private final KafkaMessageProducer kafkaMessageProducer;

    public StompChatController(KafkaMessageProducer kafkaMessageProducer) {
        this.kafkaMessageProducer = kafkaMessageProducer;
    }

    @MessageMapping("/chat/message")
    public void message(ChatMessageDto message, Principal principal) {
        ChatPrincipal chatPrincipal = authenticatedPrincipal(principal);
        message.setEventId(UUID.randomUUID().toString());
        message.setSenderId(chatPrincipal.userId());
        message.setSenderName(chatPrincipal.nickname());

        if (message.getCreatedAt() == null) {
            message.setCreatedAt(ZonedDateTime.now());
        }

        if (MessageType.ENTER.equals(message.getMessageType())) {
            message.setContent(message.getSenderName() + "님이 입장하셨습니다.");
        } else if (MessageType.LEAVE.equals(message.getMessageType())) {
            message.setContent(message.getSenderName() + "님이 퇴장하셨습니다.");
        }

        log.info("Received STOMP message: roomId={}, type={}, sender={}", 
                message.getRoomId(), message.getMessageType(), message.getSenderName());

        // Kafka is the single fanout path so every WebSocket node receives the same message.
        kafkaMessageProducer.sendMessage(message);
    }

    private ChatPrincipal authenticatedPrincipal(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof ChatPrincipal chatPrincipal) {
            return chatPrincipal;
        }
        throw new IllegalStateException("Authenticated chat principal is missing");
    }
}
