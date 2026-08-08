package com.example.chat.ws.config;

import com.example.chat.core.security.ChatPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class StompAuthenticationChannelInterceptorTest {

    private final StompAuthenticationChannelInterceptor interceptor = new StompAuthenticationChannelInterceptor();

    @Test
    void rejectsUnauthenticatedConnectAndSend() {
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.CONNECT, null), mock()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SEND, null), mock()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void acceptsAuthenticatedConnectSendAndSubscribe() {
        ChatPrincipal principal = new ChatPrincipal(1L, "alice", "Alice");
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, java.util.List.of());

        for (StompCommand command : new StompCommand[]{StompCommand.CONNECT, StompCommand.SEND, StompCommand.SUBSCRIBE}) {
            Message<byte[]> message = message(command, authentication);
            assertThat(interceptor.preSend(message, mock())).isSameAs(message);
        }
    }

    private Message<byte[]> message(StompCommand command, java.security.Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
