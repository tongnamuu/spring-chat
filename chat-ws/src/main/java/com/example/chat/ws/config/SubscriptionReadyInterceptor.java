package com.example.chat.ws.config;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionReadyInterceptor implements ExecutorChannelInterceptor {
    private final ObjectProvider<MessageChannel> outbound;

    public SubscriptionReadyInterceptor(@Qualifier("clientOutboundChannel") ObjectProvider<MessageChannel> outbound) {
        this.outbound = outbound;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        var headers = message.getHeaders();
        String destination = SimpMessageHeaderAccessor.getDestination(headers);
        if (ex != null || !(handler instanceof SimpleBrokerMessageHandler)
                || SimpMessageHeaderAccessor.getMessageType(headers) != SimpMessageType.SUBSCRIBE
                || destination == null || !destination.startsWith("/sub/chat/room/")) return;
        var reply = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        reply.setSessionId(SimpMessageHeaderAccessor.getSessionId(headers));
        reply.setSubscriptionId(SimpMessageHeaderAccessor.getSubscriptionId(headers));
        reply.setDestination(destination);
        outbound.getObject().send(MessageBuilder.createMessage(
                "{\"subscriptionReady\":true}".getBytes(StandardCharsets.UTF_8), reply.getMessageHeaders()));
    }
}
