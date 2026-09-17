package com.example.chat.common.dto;

import java.util.List;

public record ChatMessageBatch(Long roomId, long previousMessageId, List<ChatMessageDto> messages) {
    public static final String TOPIC = "chat-messages-persisted";

    public ChatMessageBatch {
        messages = List.copyOf(messages);
    }
}
