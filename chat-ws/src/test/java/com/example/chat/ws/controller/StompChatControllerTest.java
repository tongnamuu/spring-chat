package com.example.chat.ws.controller;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import com.example.chat.core.security.ChatPrincipal;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.UserRepository;
import com.example.chat.ws.producer.KafkaMessageProducer;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

class StompChatControllerTest {

    @Test
    void overwritesForgedSenderWithAuthenticatedPrincipal() {
        KafkaMessageProducer kafkaProducer = mock(KafkaMessageProducer.class);
        SimpMessageSendingOperations messaging = mock(SimpMessageSendingOperations.class);
        UserRepository userRepository = mock(UserRepository.class);
        StompChatController controller = new StompChatController(kafkaProducer, messaging, userRepository);
        ChatMessageDto message = ChatMessageDto.builder()
                .roomId(42L)
                .senderId(999L)
                .senderName("forged")
                .messageType(MessageType.TALK)
                .content("hello")
                .build();
        UserEntity user = UserEntity.builder()
                .userId(7L)
                .email("alice@example.com")
                .nickname("Current Alice")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        ChatPrincipal principal = new ChatPrincipal(7L);
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, java.util.List.of());

        controller.message(message, authentication);

        assertThat(message.getSenderId()).isEqualTo(7L);
        assertThat(message.getSenderName()).isEqualTo("Current Alice");
        verify(kafkaProducer).sendMessage(message);
        verify(messaging).convertAndSend("/sub/chat/room/42", message);
    }
}
