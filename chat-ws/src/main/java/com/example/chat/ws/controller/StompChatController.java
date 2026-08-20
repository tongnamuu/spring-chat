package com.example.chat.ws.controller;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.UserRepository;
import com.example.chat.core.security.ChatPrincipal;
import com.example.chat.ws.producer.KafkaMessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.time.ZonedDateTime;
import java.security.Principal;

@Controller
public class StompChatController {

    private static final Logger log = LoggerFactory.getLogger(StompChatController.class);

    private final KafkaMessageProducer kafkaMessageProducer;
    private final SimpMessageSendingOperations messagingTemplate;
    private final UserRepository userRepository;

    public StompChatController(
            KafkaMessageProducer kafkaMessageProducer,
            SimpMessageSendingOperations messagingTemplate,
            UserRepository userRepository
    ) {
        this.kafkaMessageProducer = kafkaMessageProducer;
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    @MessageMapping("/chat/message")
    public void message(ChatMessageDto message, Principal principal) {
        ChatPrincipal chatPrincipal = authenticatedPrincipal(principal);
        UserEntity sender = userRepository.findById(chatPrincipal.userId())
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new IllegalStateException("Active chat user is missing"));
        message.setSenderId(chatPrincipal.userId());
        message.setSenderName(sender.getNickname());

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

        // 1. Send to Kafka for async persistence and streaming
        kafkaMessageProducer.sendMessage(message);

        // 2. Broadcast immediately to subscribers of this WebSocket node
        messagingTemplate.convertAndSend("/sub/chat/room/" + message.getRoomId(), message);
    }

    private ChatPrincipal authenticatedPrincipal(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof ChatPrincipal chatPrincipal) {
            return chatPrincipal;
        }
        throw new IllegalStateException("Authenticated chat principal is missing");
    }
}
