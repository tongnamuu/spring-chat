package com.example.chat.ws.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.MapSession;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.web.socket.handler.WebSocketRegistryListener;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebSocketSessionLifecycleTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closesAllSocketsOfEndedSessionAndPreservesOtherSessions(boolean expired) throws Exception {
        var registry = new WebSocketRegistryListener();
        var backpressure = mock(FanoutBackpressure.class);
        var config = new WebSocketConfig(mock(StompAuthenticationChannelInterceptor.class), backpressure, mock(SubscriptionReadyInterceptor.class));
        ApplicationEventPublisher publisher = event -> registry.onApplicationEvent((ApplicationEvent) event);
        ReflectionTestUtils.setField(config, "eventPublisher", publisher);
        var registration = new InspectableTransport();
        config.configureWebSocketTransport(registration);
        assertThat(registration.factories()).hasSize(2);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        for (var factory : registration.factories()) handler = factory.decorate(handler);
        var session = new MapSession();
        var first = socket("first", session.getId());
        var second = socket("second", session.getId());
        var other = socket("other", new MapSession().getId());
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.afterConnectionEstablished(other);
        registry.onApplicationEvent(expired ? new SessionExpiredEvent(this, session)
                : new SessionDeletedEvent(this, session));
        verify(first).close(any(CloseStatus.class));
        verify(second).close(any(CloseStatus.class));
        verify(other, never()).close(any(CloseStatus.class));
        verify(backpressure).connected(first);
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        verify(backpressure).disconnected("first");
    }

    private WebSocketSession socket(String id, String sessionId) {
        var socket = mock(WebSocketSession.class);
        var attributes = new HashMap<String, Object>();
        SessionRepositoryMessageInterceptor.setSessionId(attributes, sessionId);
        when(socket.getId()).thenReturn(id);
        when(socket.getPrincipal()).thenReturn(() -> "authenticated-user");
        when(socket.getAttributes()).thenReturn(attributes);
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }

    private static class InspectableTransport extends WebSocketTransportRegistration {
        List<WebSocketHandlerDecoratorFactory> factories() { return getDecoratorFactories(); }
    }
}
