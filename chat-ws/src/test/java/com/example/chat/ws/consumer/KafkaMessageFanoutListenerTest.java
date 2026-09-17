package com.example.chat.ws.consumer;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.dto.ChatMessageBatch;
import java.util.List;
import com.example.chat.ws.config.FanoutBackpressure;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import com.example.chat.common.enums.MessageType;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaMessageFanoutListenerTest {

    @Test
    void broadcastsConsumedKafkaMessageToLocalRoomSubscribers() {
        SimpMessageSendingOperations messaging = mock(SimpMessageSendingOperations.class);
        FanoutBackpressure backpressure = mock(FanoutBackpressure.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        KafkaMessageFanoutListener listener = new KafkaMessageFanoutListener(messaging, backpressure, mapper);
        ChatMessageDto message = ChatMessageDto.builder()
                .eventId("event-1")
                .roomId(42L)
                .senderId(7L)
                .senderName("Alice")
                .messageType(MessageType.TALK)
                .content("hello")
                .build();

        ChatMessageBatch batch = new ChatMessageBatch(42L, 0, List.of(message));
        byte[] payload = "{\"roomId\":42}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(mapper.writeValueAsBytes(batch)).thenReturn(payload);
        listener.fanout(batch);

        verify(backpressure).reserve("/sub/chat/room/42", payload.length);
        verify(messaging).send(eq("/sub/chat/room/42"), argThat(sent ->
                java.util.Arrays.equals((byte[]) sent.getPayload(), payload)));
    }
}
