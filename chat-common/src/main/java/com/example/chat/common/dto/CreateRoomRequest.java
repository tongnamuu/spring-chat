package com.example.chat.common.dto;

import com.example.chat.common.enums.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateRoomRequest {
    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Room type is required")
    private RoomType roomType;

    @Min(value = 2, message = "Capacity must be at least 2")
    @Max(value = 50, message = "Capacity cannot exceed 50")
    private Integer maxCapacity = 50;

    private Long targetUserId;

    public CreateRoomRequest() {}

    public CreateRoomRequest(String title, RoomType roomType, Integer maxCapacity, Long targetUserId) {
        this.title = title;
        this.roomType = roomType;
        this.maxCapacity = maxCapacity != null ? maxCapacity : 50;
        this.targetUserId = targetUserId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public RoomType getRoomType() { return roomType; }
    public void setRoomType(RoomType roomType) { this.roomType = roomType; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }

    public static class Builder {
        private String title;
        private RoomType roomType;
        private Integer maxCapacity = 50;
        private Long targetUserId;

        public Builder title(String title) { this.title = title; return this; }
        public Builder roomType(RoomType roomType) { this.roomType = roomType; return this; }
        public Builder maxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; return this; }
        public Builder targetUserId(Long targetUserId) { this.targetUserId = targetUserId; return this; }

        public CreateRoomRequest build() {
            return new CreateRoomRequest(title, roomType, maxCapacity, targetUserId);
        }
    }
}
