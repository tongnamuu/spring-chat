package com.example.chat.core.entity;

import com.example.chat.common.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "users")
public class UserEntity {

    public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public UserEntity() {}

    public UserEntity(
            Long userId,
            String email,
            String nickname,
            String passwordHash,
            UserStatus status,
            ZonedDateTime deletedAt,
            ZonedDateTime createdAt
    ) {
        this.userId = userId;
        this.email = requireEmail(email);
        this.nickname = nickname;
        this.passwordHash = requirePasswordHash(passwordHash);
        this.status = status != null ? status : UserStatus.ACTIVE;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = requireEmail(email); }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = requirePasswordHash(passwordHash); }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public ZonedDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(ZonedDateTime deletedAt) { this.deletedAt = deletedAt; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isWithdrawn() {
        return status == UserStatus.WITHDRAWN;
    }

    public void withdraw(String invalidatedPasswordHash, ZonedDateTime withdrawnAt) {
        if (userId == null) {
            throw new IllegalStateException("Persisted user ID is required for withdrawal");
        }
        if (isWithdrawn()) {
            return;
        }
        email = "withdrawn-" + userId + "@deleted.invalid";
        nickname = WITHDRAWN_NICKNAME;
        passwordHash = requirePasswordHash(invalidatedPasswordHash);
        status = UserStatus.WITHDRAWN;
        deletedAt = withdrawnAt;
    }

    private static String requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        return passwordHash;
    }

    private static String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email;
    }

    public static class Builder {
        private Long userId;
        private String email;
        private String nickname;
        private String passwordHash;
        private UserStatus status = UserStatus.ACTIVE;
        private ZonedDateTime deletedAt;
        private ZonedDateTime createdAt;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder nickname(String nickname) { this.nickname = nickname; return this; }
        public Builder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public Builder status(UserStatus status) { this.status = status; return this; }
        public Builder deletedAt(ZonedDateTime deletedAt) { this.deletedAt = deletedAt; return this; }
        public Builder createdAt(ZonedDateTime createdAt) { this.createdAt = createdAt; return this; }

        public UserEntity build() {
            return new UserEntity(userId, email, nickname, passwordHash, status, deletedAt, createdAt);
        }
    }
}
