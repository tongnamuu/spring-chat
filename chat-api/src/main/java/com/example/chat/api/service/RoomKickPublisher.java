package com.example.chat.api.service;

import com.example.chat.common.dto.RoomMemberKicked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Component
public class RoomKickPublisher {
    private static final Logger log = LoggerFactory.getLogger(RoomKickPublisher.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RoomKickPublisher(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @TransactionalEventListener
    public void afterCommit(RoomMemberKicked event) {
        try {
            redis.convertAndSend(RoomMemberKicked.CHANNEL, mapper.writeValueAsString(event));
        } catch (RuntimeException failure) {
            log.warn("Failed to notify removal from room {} for user {}", event.roomId(), event.userId(), failure);
        }
    }
}
