package com.example.chat.ws.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class FanoutBackpressure implements ExecutorChannelInterceptor {
    public static final String WEIGHT_HEADER = "chatBatchWeight";
    private static final long MAX_QUEUED_BYTES = 1024 * 1024;
    private final ObjectProvider<SimpUserRegistry> registry;
    private final ConcurrentHashMap<String, PendingSession> sessions = new ConcurrentHashMap<>();

    public FanoutBackpressure(ObjectProvider<SimpUserRegistry> registry) {
        this.registry = registry;
    }

    public void connected(WebSocketSession session) {
        sessions.put(session.getId(), new PendingSession(session, new AtomicLong()));
    }

    public void disconnected(String sessionId) {
        sessions.remove(sessionId);
    }

    public void reserve(String destination, long bytes) {
        // Account before the simple broker's ordered per-session queue. Its
        // queue sits ahead of the transport buffer and otherwise has no bound.
        registry.getObject().findSubscriptions(subscription -> destination.equals(subscription.getDestination()))
                .forEach(subscription -> {
                    PendingSession pending = sessions.get(subscription.getSession().getId());
                    if (pending != null && pending.bytes().addAndGet(bytes) > MAX_QUEUED_BYTES) {
                        try {
                            pending.session().close(CloseStatus.SESSION_NOT_RELIABLE);
                        } catch (IOException ignored) {
                            // The transport is already unusable; keep the budget
                            // charged until its disconnect event cleans it up.
                        }
                    }
                });
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        Object weight = message.getHeaders().get(WEIGHT_HEADER);
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        if (sessionId == null || !(weight instanceof Number bytes)) return;
        PendingSession pending = sessions.get(sessionId);
        if (pending != null) pending.bytes().updateAndGet(value -> Math.max(0, value - bytes.longValue()));
    }

    private record PendingSession(WebSocketSession session, AtomicLong bytes) {}
}
