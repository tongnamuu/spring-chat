package com.example.chat.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;

@Entity
@Table(name = "chat_room_ban", uniqueConstraints = @UniqueConstraint(name = "uk_room_banned_user", columnNames = {"room_id", "user_id"}))
public class ChatRoomBanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "room_id", nullable = false)
    private Long roomId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "kicked_by", nullable = false)
    private Long kickedBy;
    @Column(name = "kicked_at", nullable = false)
    private ZonedDateTime kickedAt;

    protected ChatRoomBanEntity() {}

    public ChatRoomBanEntity(Long roomId, Long userId, Long kickedBy) {
        this.roomId = roomId;
        this.userId = userId;
        this.kickedBy = kickedBy;
        this.kickedAt = ZonedDateTime.now();
    }
}
