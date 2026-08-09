package com.example.chat.ws.controller;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import com.example.chat.core.security.ChatPrincipal;
import com.example.chat.ws.producer.KafkaMessageProducer;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StompChatControllerTest {

    @Test
    void overwritesForgedSenderWithAuthenticatedPrincipal() {
        KafkaMessageProducer kafkaProducer = mock(KafkaMessageProducer.class);
        SimpMessageSendingOperations messaging = mock(SimpMessageSendingOperations.class);
        StompChatController controller = new StompChatController(kafkaProducer, messaging);
        ChatMessageDto message = ChatMessageDto.builder()
                .roomId(42L)
                .senderId(999L)
                .senderName("forged")
                .messageType(MessageType.TALK)
                .content("hello")
                .build();
        ChatPrincipal principal = new ChatPrincipal(7L, "alice", "Alice");
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, java.util.List.of());

        controller.message(message, authentication);

        assertThat(message.getSenderId()).isEqualTo(7L);
        assertThat(message.getSenderName()).isEqualTo("Alice");
        verify(kafkaProducer).sendMessage(message);
        verify(messaging).convertAndSend("/sub/chat/room/42", message);
    }
}
