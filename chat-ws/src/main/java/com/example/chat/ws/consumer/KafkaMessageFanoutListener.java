package com.example.chat.ws.consumer;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.ws.producer.KafkaMessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Service
public class KafkaMessageFanoutListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageFanoutListener.class);

    private final SimpMessageSendingOperations messagingTemplate;

    public KafkaMessageFanoutListener(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(
            topics = KafkaMessageProducer.CHAT_MESSAGES_TOPIC,
            groupId = "${chat.ws.fanout.group-id:${spring.application.name:chat-ws}-${random.uuid}}"
    )
    public void fanout(ChatMessageDto message) {
        log.info("Fanout Kafka message to STOMP subscribers: roomId={}, senderId={}, type={}",
                message.getRoomId(), message.getSenderId(), message.getMessageType());

        messagingTemplate.convertAndSend("/sub/chat/room/" + message.getRoomId(), message);
    }
}
