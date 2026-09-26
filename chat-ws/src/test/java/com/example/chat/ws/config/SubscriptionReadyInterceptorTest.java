package com.example.chat.ws.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionReadyInterceptorTest {
    @Test
    @SuppressWarnings("unchecked")
    void onlySuccessfulBrokerSubscriptionNotifiesItsOwnSession() {
        var outbound = mock(MessageChannel.class);
        ObjectProvider<MessageChannel> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(outbound);
        var interceptor = new SubscriptionReadyInterceptor(provider);
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
        headers.setSessionId("session-a");
        headers.setSubscriptionId("subscription-a");
        headers.setDestination("/sub/chat/room/42");
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        var broker = mock(SimpleBrokerMessageHandler.class);
        interceptor.afterMessageHandled(message, outbound, mock(MessageHandler.class), null);
        interceptor.afterMessageHandled(message, outbound, broker, new IllegalStateException());
        verifyNoInteractions(outbound);
        when(outbound.send(any())).thenAnswer(invocation -> {
            org.springframework.messaging.Message<?> reply = invocation.getArgument(0);
            assertEquals("session-a", SimpMessageHeaderAccessor.getSessionId(reply.getHeaders()));
            assertEquals("subscription-a", SimpMessageHeaderAccessor.getSubscriptionId(reply.getHeaders()));
            assertEquals("{\"subscriptionReady\":true}", new String((byte[]) reply.getPayload(), StandardCharsets.UTF_8));
            return true;
        });
        interceptor.afterMessageHandled(message, outbound, broker, null);
        verify(outbound).send(any());
    }
}
