package com.example.chat.ws.consumer;

import com.example.chat.common.dto.ChatMessageBatch;
import com.example.chat.ws.config.FanoutBackpressure;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Service
public class KafkaMessageFanoutListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageFanoutListener.class);

    private final SimpMessageSendingOperations messagingTemplate;
    private final FanoutBackpressure backpressure;
    private final ObjectMapper mapper;

    public KafkaMessageFanoutListener(SimpMessageSendingOperations messagingTemplate,
            FanoutBackpressure backpressure, ObjectMapper mapper) {
        this.messagingTemplate = messagingTemplate;
        this.backpressure = backpressure;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = ChatMessageBatch.TOPIC,
            groupId = "${chat.ws.fanout.group-id:${spring.application.name:chat-ws}-${random.uuid}}"
    )
    public void fanout(ChatMessageBatch batch) {
        log.debug("Fanout persisted batch: roomId={}, size={}", batch.roomId(), batch.messages().size());
        String destination = "/sub/chat/room/" + batch.roomId();
        byte[] payload = mapper.writeValueAsBytes(batch);
        backpressure.reserve(destination, payload.length);
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        headers.setHeader(FanoutBackpressure.WEIGHT_HEADER, payload.length);
        headers.setLeaveMutable(true);
        messagingTemplate.send(destination, MessageBuilder.createMessage(payload, headers.getMessageHeaders()));
    }
}
