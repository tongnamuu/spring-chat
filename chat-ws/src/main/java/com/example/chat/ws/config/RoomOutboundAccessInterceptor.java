package com.example.chat.ws.config;

import com.example.chat.core.repository.ChatRoomMemberRepository;
import com.example.chat.core.security.ChatPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.OrderedMessageChannelDecorator;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class RoomOutboundAccessInterceptor implements ExecutorChannelInterceptor {
    private static final Pattern ROOM = Pattern.compile("/sub/chat/room/([1-9][0-9]*)");
    private final ConcurrentHashMap<String, Long> sessionUsers = new ConcurrentHashMap<>();
    private final ChatRoomMemberRepository members;

    public RoomOutboundAccessInterceptor(ChatRoomMemberRepository members) {
        this.members = members;
    }

    public static Long roomId(String destination) {
        if (destination == null) return null;
        var match = ROOM.matcher(destination);
        if (!match.matches()) return null;
        try { return Long.valueOf(match.group(1)); }
        catch (NumberFormatException ignored) { return null; }
    }

    public void connected(WebSocketSession session) {
        if (session.getPrincipal() instanceof Authentication auth
                && auth.getPrincipal() instanceof ChatPrincipal principal) {
            sessionUsers.put(session.getId(), principal.userId());
        }
    }

    public void disconnected(String sessionId) { sessionUsers.remove(sessionId); }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        Long roomId = roomId(destination);
        if (roomId == null) return message;
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        Long userId = sessionId == null ? null : sessionUsers.get(sessionId);
        boolean allowed = false;
        try {
            allowed = userId != null && members.existsByRoomIdAndUserId(roomId, userId);
            return allowed ? message : null;
        } finally {
            if (!allowed) {
                Runnable next = OrderedMessageChannelDecorator.getNextMessageTask(message);
                if (next != null) next.run();
            }
        }
    }
}
