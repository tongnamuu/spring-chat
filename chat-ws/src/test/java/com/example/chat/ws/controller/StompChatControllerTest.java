package com.example.chat.ws.controller;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import com.example.chat.core.security.ChatPrincipal;
import com.example.chat.ws.producer.KafkaMessageProducer;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompChatControllerTest {

    @Test
    void validatesEntireBatchBeforePublishingAnyMessage() {
        KafkaMessageProducer producer = mock(KafkaMessageProducer.class);
        StompChatController controller = new StompChatController(producer);
        ChatMessageDto valid = ChatMessageDto.builder().roomId(42L).messageType(MessageType.TALK).content("hello").build();
        ChatMessageDto invalid = ChatMessageDto.builder().roomId(42L).messageType(MessageType.TALK).content("x".repeat(2001)).build();
        assertThatThrownBy(() -> controller.messages(java.util.List.of(valid, invalid), null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(producer);
    }

    @Test
    void overwritesForgedSenderWithAuthenticatedPrincipal() {
        KafkaMessageProducer kafkaProducer = mock(KafkaMessageProducer.class);
        when(kafkaProducer.sendMessage(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        StompChatController controller = new StompChatController(kafkaProducer);
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

        message.setSourceNode("forged-node");
        controller.message(message, authentication);
        assertThat(message.getSourceNode()).isNull();

        assertThat(message.getSenderId()).isEqualTo(7L);
        assertThat(message.getSenderName()).isEqualTo("Alice");
        assertThat(message.getEventId()).isNotBlank();
        verify(kafkaProducer).sendMessage(message);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "debugNode", true);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "nodeId", "chat-ws-a");
        message.setSourceNode("forged-node");
        controller.message(message, authentication);
        assertThat(message.getSourceNode()).isEqualTo("chat-ws-a");
    }
}
