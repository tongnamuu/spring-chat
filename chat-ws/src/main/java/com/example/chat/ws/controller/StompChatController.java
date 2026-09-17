package com.example.chat.ws.controller;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import com.example.chat.core.security.ChatPrincipal;
import com.example.chat.ws.producer.KafkaMessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.time.ZonedDateTime;
import java.security.Principal;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Controller
public class StompChatController {

    private static final Logger log = LoggerFactory.getLogger(StompChatController.class);

    private final KafkaMessageProducer kafkaMessageProducer;

    @org.springframework.beans.factory.annotation.Value("${chat.ws.debug-node:false}")
    private boolean debugNode;

    @org.springframework.beans.factory.annotation.Value("${chat.ws.node-id:chat-ws}")
    private String nodeId;

    public StompChatController(KafkaMessageProducer kafkaMessageProducer) {
        this.kafkaMessageProducer = kafkaMessageProducer;
    }

    @MessageMapping("/chat/messages")
    @SendToUser(value = "/queue/publish-results", broadcast = false)
    public CompletableFuture<PublishResult> messages(List<ChatMessageDto> messages, Principal principal) {
        if (messages == null || messages.isEmpty() || messages.size() > 100) {
            throw new IllegalArgumentException("A batch must contain 1 to 100 messages");
        }
        messages.forEach(this::validateMessage);
        var publications = messages.stream().map(message -> message(message, principal)).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(publications).thenApply(ignored -> new PublishResult(true));
    }

    @MessageMapping("/chat/message")
    @SendToUser(value = "/queue/publish-results", broadcast = false)
    public CompletableFuture<PublishResult> message(ChatMessageDto message, Principal principal) {
        ChatPrincipal chatPrincipal = authenticatedPrincipal(principal);
        validateMessage(message);
        message.setMessageId(null);
        message.setSourceNode(debugNode ? nodeId : null);
        message.setEventId(UUID.randomUUID().toString());
        message.setSenderId(chatPrincipal.userId());
        message.setSenderName(chatPrincipal.nickname());
        message.setCreatedAt(ZonedDateTime.now());

        if (MessageType.ENTER.equals(message.getMessageType())) {
            message.setContent(message.getSenderName() + "님이 입장하셨습니다.");
        } else if (MessageType.LEAVE.equals(message.getMessageType())) {
            message.setContent(message.getSenderName() + "님이 퇴장하셨습니다.");
        }

        log.debug("Received STOMP message: roomId={}, type={}, sender={}",
                message.getRoomId(), message.getMessageType(), message.getSenderName());
        return kafkaMessageProducer.sendMessage(message).thenApply(ignored -> new PublishResult(true));
    }

    @MessageExceptionHandler
    @SendToUser(value = "/queue/publish-results", broadcast = false)
    public PublishResult publicationFailed(Exception failure) {
        log.warn("Chat publication failed", failure);
        return new PublishResult(false);
    }

    public record PublishResult(boolean accepted) {}

    private void validateMessage(ChatMessageDto message) {
        if (message == null || message.getRoomId() == null || message.getMessageType() == null
                || MessageType.SYSTEM.equals(message.getMessageType())
                || (MessageType.TALK.equals(message.getMessageType())
                && (message.getContent() == null || message.getContent().isBlank() || message.getContent().length() > 2000))) {
            throw new IllegalArgumentException("A room, message type and content up to 2000 characters are required");
        }
    }

    private ChatPrincipal authenticatedPrincipal(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof ChatPrincipal chatPrincipal) {
            return chatPrincipal;
        }
        throw new IllegalStateException("Authenticated chat principal is missing");
    }
}
