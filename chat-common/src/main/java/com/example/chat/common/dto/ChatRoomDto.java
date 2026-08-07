package com.example.chat.common.dto;

import com.example.chat.common.enums.RoomType;
import java.time.ZonedDateTime;

public class ChatRoomDto {
    private Long roomId;
    private String title;
    private RoomType roomType;
    private String inviteCode;
    private Integer maxCapacity;
    private Integer currentCount;
    private Long createdBy;
    private ZonedDateTime createdAt;

    public ChatRoomDto() {}

    public ChatRoomDto(Long roomId, String title, RoomType roomType, String inviteCode, Integer maxCapacity, Integer currentCount, Long createdBy, ZonedDateTime createdAt) {
        this.roomId = roomId;
        this.title = title;
        this.roomType = roomType;
        this.inviteCode = inviteCode;
        this.maxCapacity = maxCapacity;
        this.currentCount = currentCount;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public RoomType getRoomType() { return roomType; }
    public void setRoomType(RoomType roomType) { this.roomType = roomType; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public Integer getCurrentCount() { return currentCount; }
    public void setCurrentCount(Integer currentCount) { this.currentCount = currentCount; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public static class Builder {
        private Long roomId;
        private String title;
        private RoomType roomType;
        private String inviteCode;
        private Integer maxCapacity;
        private Integer currentCount;
        private Long createdBy;
        private ZonedDateTime createdAt;

        public Builder roomId(Long roomId) { this.roomId = roomId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder roomType(RoomType roomType) { this.roomType = roomType; return this; }
        public Builder inviteCode(String inviteCode) { this.inviteCode = inviteCode; return this; }
        public Builder maxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; return this; }
        public Builder currentCount(Integer currentCount) { this.currentCount = currentCount; return this; }
        public Builder createdBy(Long createdBy) { this.createdBy = createdBy; return this; }
        public Builder createdAt(ZonedDateTime createdAt) { this.createdAt = createdAt; return this; }

        public ChatRoomDto build() {
            return new ChatRoomDto(roomId, title, roomType, inviteCode, maxCapacity, currentCount, createdBy, createdAt);
        }
    }
}
