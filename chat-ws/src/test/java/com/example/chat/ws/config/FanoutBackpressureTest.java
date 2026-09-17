package com.example.chat.ws.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FanoutBackpressureTest {
    @Test
    @SuppressWarnings("unchecked")
    void closesOnlyBackloggedSessionAndReleasesCompletedDeliveryBudget() throws Exception {
        SimpUserRegistry registry = mock(SimpUserRegistry.class);
        ObjectProvider<SimpUserRegistry> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(registry);
        FanoutBackpressure guard = new FanoutBackpressure(provider);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("session-1");
        SimpSession session = mock(SimpSession.class);
        when(session.getId()).thenReturn("session-1");
        SimpSubscription subscription = mock(SimpSubscription.class);
        when(subscription.getSession()).thenReturn(session);
        when(registry.findSubscriptions(any())).thenReturn(Set.of(subscription));
        guard.connected(socket);

        for (int i = 0; i < 10; i++) {
            guard.reserve("/sub/chat/room/42", 600000);
            var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setSessionId("session-1");
            headers.setHeader(FanoutBackpressure.WEIGHT_HEADER, 600000);
            guard.afterMessageHandled(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), mock(), mock(), null);
        }
        verify(socket, never()).close(any());
        guard.reserve("/sub/chat/room/42", 600000);
        guard.reserve("/sub/chat/room/42", 600000);
        verify(socket).close(CloseStatus.SESSION_NOT_RELIABLE);
        guard.disconnected("session-1");
        guard.reserve("/sub/chat/room/42", 600000);
        verify(socket, times(1)).close(any());
    }
}
