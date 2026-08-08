package com.example.chat.ws.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.EnumSet;

@Component
public class StompAuthenticationChannelInterceptor implements ChannelInterceptor {

    private static final EnumSet<StompCommand> AUTHENTICATED_COMMANDS = EnumSet.of(
            StompCommand.CONNECT,
            StompCommand.SEND,
            StompCommand.SUBSCRIBE
    );

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !AUTHENTICATED_COMMANDS.contains(accessor.getCommand())) {
            return message;
        }

        Principal user = accessor.getUser();
        if (!(user instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authenticated STOMP session required");
        }
        return message;
    }
}
