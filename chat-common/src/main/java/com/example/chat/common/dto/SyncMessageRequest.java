package com.example.chat.common.dto;

public class SyncMessageRequest {
    private Long roomId;
    private Long lastReceivedMessageId;

    public SyncMessageRequest() {}

    public SyncMessageRequest(Long roomId, Long lastReceivedMessageId) {
        this.roomId = roomId;
        this.lastReceivedMessageId = lastReceivedMessageId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }

    public Long getLastReceivedMessageId() { return lastReceivedMessageId; }
    public void setLastReceivedMessageId(Long lastReceivedMessageId) { this.lastReceivedMessageId = lastReceivedMessageId; }

    public static class Builder {
        private Long roomId;
        private Long lastReceivedMessageId;

        public Builder roomId(Long roomId) { this.roomId = roomId; return this; }
        public Builder lastReceivedMessageId(Long lastReceivedMessageId) { this.lastReceivedMessageId = lastReceivedMessageId; return this; }

        public SyncMessageRequest build() {
            return new SyncMessageRequest(roomId, lastReceivedMessageId);
        }
    }
}
