package com.example.chat.ws.config;

import com.example.chat.common.dto.CreateRoomRequest;
import com.example.chat.common.dto.RoomMemberKicked;
import com.example.chat.common.enums.RoomType;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.UserRepository;
import com.example.chat.core.security.ChatPrincipal;
import com.example.chat.core.service.ChatRoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.kafka.listener.auto-startup=false", "spring.jpa.show-sql=false"})
@Testcontainers
class RoomKickWebSocketIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withUrlParam("connectTimeout", "5").withUrlParam("socketTimeout", "10");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired ChatRoomService rooms;
    @Autowired SessionRepository sessions;
    @Autowired SimpMessageSendingOperations messaging;
    @Autowired SimpUserRegistry registry;
    @Autowired org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler broker;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper mapper;

    @Test
    void existingSocketsLoseOnlyRemovedRoomAndEveryTabReceivesNotification() throws Exception {
        var owner = user("owner");
        var member = user("member");
        var room = rooms.createRoom(owner.getUserId(), CreateRoomRequest.builder().title("room").roomType(RoomType.GROUP_PUBLIC).build());
        var other = rooms.createRoom(owner.getUserId(), CreateRoomRequest.builder().title("other").roomType(RoomType.GROUP_PUBLIC).build());
        rooms.joinRoomByInviteCode(member.getUserId(), room.getInviteCode());
        rooms.joinRoomByInviteCode(member.getUserId(), other.getInviteCode());
        try (var first = connect(member); var second = connect(member); var ownerSocket = connect(owner)) {
            String destination = "/sub/chat/room/" + room.getRoomId();
            first.send("SUBSCRIBE\nid:room\ndestination:" + destination + "\n\n\0");
            first.send("SUBSCRIBE\nid:other\ndestination:/sub/chat/room/" + other.getRoomId() + "\n\n\0");
            first.send("SUBSCRIBE\nid:publish-results\ndestination:/user/queue/publish-results\n\n\0");
            first.send("SUBSCRIBE\nid:events\ndestination:/user/queue/room-events\n\n\0");
            second.send("SUBSCRIBE\nid:second-room\ndestination:" + destination + "\n\n\0");
            second.send("SUBSCRIBE\nid:events\ndestination:/user/queue/room-events\n\n\0");
            ownerSocket.send("SUBSCRIBE\nid:room\ndestination:" + destination + "\n\n\0");
            await().atMost(Duration.ofSeconds(5)).until(() -> registry.findSubscriptions(s -> true).size() == 7);
            messaging.convertAndSend(destination, "before-kick");
            assertThat(first.next()).contains("before-kick");
            assertThat(second.next()).contains("before-kick");
            assertThat(ownerSocket.next()).contains("before-kick");
            rooms.kickMember(owner.getUserId(), room.getRoomId(), member.getUserId());
            // Simulate the API's committed event on the shared Redis channel.
            redis.convertAndSend(RoomMemberKicked.CHANNEL, mapper.writeValueAsString(
                    new RoomMemberKicked(room.getRoomId(), member.getUserId(), member.getUsername())));
            assertThat(first.next()).contains("KICKED");
            assertThat(second.next()).contains("KICKED");
            // Neither socket sends UNSUBSCRIBE: both are evicted by the server itself.
            var deliveryHeaders = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(
                    org.springframework.messaging.simp.SimpMessageType.MESSAGE);
            deliveryHeaders.setDestination(destination);
            var roomSubscriptions = broker.getSubscriptionRegistry().findSubscriptions(
                    org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], deliveryHeaders.getMessageHeaders()));
            assertThat(roomSubscriptions).hasSize(1); // Only the owner's session remains.
            assertThat(registry.getUser(member.getUsername()).getSessions().stream()
                    .flatMap(session -> session.getSubscriptions().stream())
                    .filter(subscription -> destination.equals(subscription.getDestination()))).isEmpty();
            messaging.convertAndSend(destination, "secret-after-kick");
            messaging.convertAndSend("/sub/chat/room/" + other.getRoomId(), "other-room-allowed");
            assertThat(ownerSocket.next()).contains("secret-after-kick");
            assertThat(first.next()).contains("other-room-allowed").doesNotContain("secret-after-kick");
            assertThat(first.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
            first.send("SEND\ndestination:/pub/chat/message\ncontent-type:application/json\n\n{\"roomId\":"
                    + room.getRoomId() + ",\"messageType\":\"TALK\",\"content\":\"forbidden\"}\0");
            assertThat(first.next()).contains("\"accepted\":false");
            // A fresh subscription to the removed room is rejected at the STOMP boundary.
            second.send("SUBSCRIBE\nid:forbidden\ndestination:" + destination + "\n\n\0");
            // With preserveReceiveOrder, the ordered channel logs a denied frame and continues.
            // Use a subsequent allowed subscription as a barrier proving the denial was processed.
            second.send("SUBSCRIBE\nid:after-denial\ndestination:/user/queue/publish-results\n\n\0");
            await().atMost(Duration.ofSeconds(5)).until(() -> !registry.findSubscriptions(
                    subscription -> "after-denial".equals(subscription.getId())).isEmpty());
            // The user registry is event bookkeeping; only the broker's registry controls delivery.
            var headers = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(
                    org.springframework.messaging.simp.SimpMessageType.MESSAGE);
            headers.setDestination(destination);
            var subscriptions = broker.getSubscriptionRegistry().findSubscriptions(
                    org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
            assertThat(subscriptions.values().stream().flatMap(List::stream).toList()).doesNotContain("forbidden");
            messaging.convertAndSend(destination, "still-forbidden");
            assertThat(second.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
        }
    }

    private UserEntity user(String name) {
        return users.save(UserEntity.builder().username(name).nickname(name).passwordHash("hash").build());
    }

    @SuppressWarnings("unchecked")
    private Socket connect(UserEntity user) throws Exception {
        var session = sessions.createSession();
        var principal = new ChatPrincipal(user.getUserId(), user.getUsername(), user.getNickname());
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())));
        sessions.save(session);
        var listener = new Socket();
        String cookie = Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));
        listener.socket = HttpClient.newHttpClient().newWebSocketBuilder().header("Cookie", "CHAT_SESSION=" + cookie)
                .buildAsync(URI.create("ws://localhost:" + port + "/ws-stomp/websocket"), listener).get(5, TimeUnit.SECONDS);
        listener.send("CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0");
        assertThat(listener.next()).startsWith("CONNECTED");
        return listener;
    }

    private static class Socket implements WebSocket.Listener, AutoCloseable {
        WebSocket socket;
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        public void onOpen(WebSocket socket) { socket.request(1); }
        public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            partial.append(text);
            if (last) { frames.add(partial.toString()); partial.setLength(0); }
            socket.request(1);
            return null;
        }
        public CompletionStage<?> onBinary(WebSocket socket, java.nio.ByteBuffer data, boolean last) {
            return onText(socket, StandardCharsets.UTF_8.decode(data), last);
        }
        void send(String frame) { socket.sendText(frame, true).join(); }
        String next() throws InterruptedException {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertThat(frame).as("Expected STOMP frame").isNotNull();
            return frame;
        }
        public void close() { socket.abort(); }
    }
}
