package com.example.chat.ws.consumer;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaMessageFanoutListenerTest {

    @Test
    void broadcastsConsumedKafkaMessageToLocalRoomSubscribers() {
        SimpMessageSendingOperations messaging = mock(SimpMessageSendingOperations.class);
        KafkaMessageFanoutListener listener = new KafkaMessageFanoutListener(messaging);
        ChatMessageDto message = ChatMessageDto.builder()
                .roomId(42L)
                .senderId(7L)
                .senderName("Alice")
                .messageType(MessageType.TALK)
                .content("hello")
                .build();

        listener.fanout(message);

        verify(messaging).convertAndSend("/sub/chat/room/42", message);
    }
}
