package com.example.chat.common.dto;

import com.example.chat.common.enums.MessageType;
import java.time.ZonedDateTime;

public class ChatMessageDto {
    private Long messageId;
    private Long roomId;
    private Long senderId;
    private String senderName;
    private MessageType messageType;
    private String content;
    private ZonedDateTime createdAt;

    public ChatMessageDto() {}

    public ChatMessageDto(Long messageId, Long roomId, Long senderId, String senderName, MessageType messageType, String content, ZonedDateTime createdAt) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.messageType = messageType;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public static class Builder {
        private Long messageId;
        private Long roomId;
        private Long senderId;
        private String senderName;
        private MessageType messageType;
        private String content;
        private ZonedDateTime createdAt;

        public Builder messageId(Long messageId) { this.messageId = messageId; return this; }
        public Builder roomId(Long roomId) { this.roomId = roomId; return this; }
        public Builder senderId(Long senderId) { this.senderId = senderId; return this; }
        public Builder senderName(String senderName) { this.senderName = senderName; return this; }
        public Builder messageType(MessageType messageType) { this.messageType = messageType; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder createdAt(ZonedDateTime createdAt) { this.createdAt = createdAt; return this; }

        public ChatMessageDto build() {
            return new ChatMessageDto(messageId, roomId, senderId, senderName, messageType, content, createdAt);
        }
    }
}
