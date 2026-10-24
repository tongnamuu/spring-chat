package com.example.chat.ws.config;

import com.example.chat.common.dto.RoomMemberKicked;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;

@Configuration
public class RoomKickNotifications {
    @Bean
    public RedisMessageListenerContainer roomKickListener(RedisConnectionFactory connections,
            SimpMessageSendingOperations messaging, ObjectMapper mapper, ObjectProvider<SimpUserRegistry> registry,
            ObjectProvider<SimpleBrokerMessageHandler> broker, ApplicationEventPublisher events) {
        var listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(connections);
        listener.addMessageListener((message, pattern) -> {
            var event = mapper.readValue(message.getBody(), RoomMemberKicked.class);
            var user = registry.getObject().getUser(event.username());
            if (user == null) return;
            String destination = "/sub/chat/room/" + event.roomId();
            for (var session : user.getSessions()) {
                for (var subscription : session.getSubscriptions()) {
                    if (!destination.equals(subscription.getDestination())) continue;
                    var unsubscribe = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
                    unsubscribe.setSessionId(session.getId());
                    unsubscribe.setSubscriptionId(subscription.getId());
                    var frame = MessageBuilder.createMessage(new byte[0], unsubscribe.getMessageHeaders());
                    broker.getObject().getSubscriptionRegistry().unregisterSubscription(frame);
                    events.publishEvent(new SessionUnsubscribeEvent(this, frame, user.getPrincipal()));
                }
                var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                headers.setSessionId(session.getId());
                headers.setLeaveMutable(true);
                messaging.convertAndSendToUser(event.username(), "/queue/room-events",
                        Map.of("type", "KICKED", "roomId", event.roomId()), headers.getMessageHeaders());
            }
        }, new ChannelTopic(RoomMemberKicked.CHANNEL));
        return listener;
    }
}
