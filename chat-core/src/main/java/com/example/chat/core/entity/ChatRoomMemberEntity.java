package com.example.chat.core.entity;

import com.example.chat.common.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "chat_room_member",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_user", columnNames = {"room_id", "user_id"})
    }
)
public class ChatRoomMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_member_id")
    private Long roomMemberId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.MEMBER;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId = 0L;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private ZonedDateTime joinedAt;

    public ChatRoomMemberEntity() {}

    public ChatRoomMemberEntity(Long roomMemberId, Long roomId, Long userId, Role role, Long lastReadMessageId, ZonedDateTime joinedAt) {
        this.roomMemberId = roomMemberId;
        this.roomId = roomId;
        this.userId = userId;
        this.role = role != null ? role : Role.MEMBER;
        this.lastReadMessageId = lastReadMessageId != null ? lastReadMessageId : 0L;
        this.joinedAt = joinedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getRoomMemberId() { return roomMemberId; }
    public void setRoomMemberId(Long roomMemberId) { this.roomMemberId = roomMemberId; }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Long getLastReadMessageId() { return lastReadMessageId; }
    public void setLastReadMessageId(Long lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; }

    public ZonedDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(ZonedDateTime joinedAt) { this.joinedAt = joinedAt; }

    public static class Builder {
        private Long roomMemberId;
        private Long roomId;
        private Long userId;
        private Role role = Role.MEMBER;
        private Long lastReadMessageId = 0L;
        private ZonedDateTime joinedAt;

        public Builder roomMemberId(Long roomMemberId) { this.roomMemberId = roomMemberId; return this; }
        public Builder roomId(Long roomId) { this.roomId = roomId; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder role(Role role) { this.role = role; return this; }
        public Builder lastReadMessageId(Long lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; return this; }
        public Builder joinedAt(ZonedDateTime joinedAt) { this.joinedAt = joinedAt; return this; }

        public ChatRoomMemberEntity build() {
            return new ChatRoomMemberEntity(roomMemberId, roomId, userId, role, lastReadMessageId, joinedAt);
        }
    }
}
