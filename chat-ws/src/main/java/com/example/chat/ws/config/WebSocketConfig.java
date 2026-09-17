package com.example.chat.ws.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.session.Session;
import org.springframework.session.web.socket.config.annotation.AbstractSessionWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractSessionWebSocketMessageBrokerConfigurer<Session> {

    private final StompAuthenticationChannelInterceptor authenticationInterceptor;
    private final FanoutBackpressure backpressure;

    public WebSocketConfig(StompAuthenticationChannelInterceptor authenticationInterceptor, FanoutBackpressure backpressure) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.backpressure = backpressure;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /sub topic for subscribing to messages
        registry.enableSimpleBroker("/sub", "/queue");
        registry.setPreservePublishOrder(true);
        // /pub prefix for message routing
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        super.configureClientInboundChannel(registration);
        registration.interceptors(authenticationInterceptor);
        registration.taskExecutor().corePoolSize(4).maxPoolSize(16).queueCapacity(1000);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(backpressure);
        registration.taskExecutor().corePoolSize(4).maxPoolSize(16).queueCapacity(1000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(256 * 1024)
                .setSendBufferSizeLimit(1024 * 1024)
                .setSendTimeLimit(5000);
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                backpressure.connected(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                backpressure.disconnected(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }

    @Override
    protected void configureStompEndpoints(StompEndpointRegistry registry) {
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint("/ws-stomp").withSockJS();
    }
}
