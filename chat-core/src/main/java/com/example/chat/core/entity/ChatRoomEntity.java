package com.example.chat.core.entity;

import com.example.chat.common.enums.RoomType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "chat_room")
public class ChatRoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    @Column(name = "invite_code", nullable = false, unique = true, length = 16)
    private String inviteCode;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity = 50;

    @Column(name = "current_count", nullable = false)
    private Integer currentCount = 1;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public ChatRoomEntity() {}

    public ChatRoomEntity(Long roomId, String title, RoomType roomType, String inviteCode, Integer maxCapacity, Integer currentCount, Long createdBy, Long version, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        this.roomId = roomId;
        this.title = title;
        this.roomType = roomType;
        this.inviteCode = inviteCode;
        this.maxCapacity = maxCapacity != null ? maxCapacity : 50;
        this.currentCount = currentCount != null ? currentCount : 1;
        this.createdBy = createdBy;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static class Builder {
        private Long roomId;
        private String title;
        private RoomType roomType;
        private String inviteCode;
        private Integer maxCapacity = 50;
        private Integer currentCount = 1;
        private Long createdBy;
        private Long version;
        private ZonedDateTime createdAt;
        private ZonedDateTime updatedAt;

        public Builder roomId(Long roomId) { this.roomId = roomId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder roomType(RoomType roomType) { this.roomType = roomType; return this; }
        public Builder inviteCode(String inviteCode) { this.inviteCode = inviteCode; return this; }
        public Builder maxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; return this; }
        public Builder currentCount(Integer currentCount) { this.currentCount = currentCount; return this; }
        public Builder createdBy(Long createdBy) { this.createdBy = createdBy; return this; }
        public Builder version(Long version) { this.version = version; return this; }
        public Builder createdAt(ZonedDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public ChatRoomEntity build() {
            return new ChatRoomEntity(roomId, title, roomType, inviteCode, maxCapacity, currentCount, createdBy, version, createdAt, updatedAt);
        }
    }
}
