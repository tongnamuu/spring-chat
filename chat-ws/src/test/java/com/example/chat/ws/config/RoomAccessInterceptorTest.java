package com.example.chat.ws.config;

import com.example.chat.common.enums.UserStatus;
import com.example.chat.common.exception.ChatException;
import com.example.chat.common.exception.ErrorCode;
import com.example.chat.core.repository.ChatRoomMemberRepository;
import com.example.chat.core.repository.UserRepository;
import com.example.chat.core.security.ChatPrincipal;
import com.example.chat.core.service.ChatRoomService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.OrderedMessageChannelDecorator;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomAccessInterceptorTest {
    private final ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
    private final RoomOutboundAccessInterceptor outbound = new RoomOutboundAccessInterceptor(members);
    private final ChatRoomService rooms = mock(ChatRoomService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StompAuthenticationChannelInterceptor inbound = new StompAuthenticationChannelInterceptor(users, rooms);
    private final UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
            new ChatPrincipal(7L, "alice", "Alice"), null, List.of());

    @Test
    void roomSubscriptionRequiresMembershipAndRejectsBrokerBypasses() {
        when(users.existsByUserIdAndStatus(7L, UserStatus.ACTIVE)).thenReturn(true);
        var allowed = frame(StompCommand.SUBSCRIBE, "/sub/chat/room/42");
        assertThat(inbound.preSend(allowed, mock())).isSameAs(allowed);
        verify(rooms).requireMember(7L, 42L);
        doThrow(new ChatException(ErrorCode.NOT_ROOM_MEMBER)).when(rooms).requireMember(7L, 42L);
        assertThatThrownBy(() -> inbound.preSend(allowed, mock())).isInstanceOf(ChatException.class);
        for (String destination : List.of("/sub/chat/room/*", "/sub/chat/**", "/queue/room-events-user-other",
                "/user/bob/queue/room-events", "/sub/chat/room/42/", "/sub/chat/room/999999999999999999999")) {
            assertThatThrownBy(() -> inbound.preSend(frame(StompCommand.SUBSCRIBE, destination), mock()))
                    .isInstanceOf(AccessDeniedException.class);
        }
        for (String destination : List.of("/sub/chat/room/42", "/queue/room-events", "/user/queue/room-events")) {
            assertThatThrownBy(() -> inbound.preSend(frame(StompCommand.SEND, destination), mock()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void dropsQueuedDeliveryAfterRemovalWithoutStallingOtherRoomMessages() {
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("session");
        when(socket.getPrincipal()).thenReturn(auth);
        outbound.connected(socket);
        when(members.existsByRoomIdAndUserId(42L, 7L)).thenReturn(true);
        when(members.existsByRoomIdAndUserId(99L, 7L)).thenReturn(true);
        var tasks = new ArrayList<Runnable>();
        var channel = new ExecutorSubscribableChannel(tasks::add);
        channel.addInterceptor(outbound);
        OrderedMessageChannelDecorator.configureInterceptor(channel, true);
        var delivered = new ArrayList<Message<?>>();
        channel.subscribe(delivered::add);
        var ordered = new OrderedMessageChannelDecorator(channel, org.apache.commons.logging.LogFactory.getLog(getClass()));
        var revoked = delivery("/sub/chat/room/42");
        var other = delivery("/sub/chat/room/99");
        ordered.send(revoked);
        ordered.send(other);
        // Authorization changes while the first delivery is queued on the executor.
        when(members.existsByRoomIdAndUserId(42L, 7L)).thenReturn(false);
        while (!tasks.isEmpty()) tasks.removeFirst().run();
        assertThat(delivered).containsExactly(other);
        outbound.disconnected("session");
        assertThat(outbound.beforeHandle(other, channel, mock())).isNull();
    }

    private Message<byte[]> frame(StompCommand command, String destination) {
        var headers = StompHeaderAccessor.create(command);
        headers.setUser(auth);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private Message<byte[]> delivery(String destination) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(destination);
        headers.setSessionId("session");
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
