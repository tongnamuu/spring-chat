package com.example.chat.ws.producer;

import com.example.chat.common.dto.ChatMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageProducer.class);
    public static final String CHAT_MESSAGES_TOPIC = "chat-messages";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaMessageProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(ChatMessageDto messageDto) {
        log.info("Producing message to Kafka topic {}: roomId={}, senderId={}", 
                CHAT_MESSAGES_TOPIC, messageDto.getRoomId(), messageDto.getSenderId());
        
        kafkaTemplate.send(CHAT_MESSAGES_TOPIC, String.valueOf(messageDto.getRoomId()), messageDto);
    }
}
