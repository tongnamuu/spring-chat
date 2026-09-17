package com.example.chat.consumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaRetryConfig {
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        // Stop progress on failed persistence/publication instead of skipping data
        // after the default retry limit. Alert on sustained consumer lag.
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setClassifications(java.util.Map.of(Exception.class, true), true);
        return handler;
    }
}
