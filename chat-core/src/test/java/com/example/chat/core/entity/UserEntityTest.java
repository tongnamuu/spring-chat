package com.example.chat.core.entity;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserEntityTest {

    @Test
    void requiresPasswordHash() {
        assertThatThrownBy(() -> UserEntity.builder()
                .email("alice@example.com")
                .nickname("Alice")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password hash is required");

        assertThatThrownBy(() -> UserEntity.builder()
                .email("alice@example.com")
                .nickname("Alice")
                .passwordHash(" ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password hash is required");
    }

    @Test
    void requiresEmail() {
        assertThatThrownBy(() -> UserEntity.builder()
                .nickname("Alice")
                .passwordHash("password-hash")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email is required");
    }

    @Test
    void withdrawalAnonymizesIdentityAndIsIdempotent() {
        UserEntity user = UserEntity.builder()
                .userId(42L)
                .email("alice@example.com")
                .nickname("Alice")
                .passwordHash("original-hash")
                .build();
        ZonedDateTime withdrawnAt = ZonedDateTime.parse("2026-08-09T10:00:00+09:00");

        user.withdraw("invalidated-hash", withdrawnAt);
        user.withdraw("second-hash", withdrawnAt.plusHours(1));

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getEmail()).isEqualTo("withdrawn-42@deleted.invalid");
        assertThat(user.getNickname()).isEqualTo(UserEntity.WITHDRAWN_NICKNAME);
        assertThat(user.getPasswordHash()).isEqualTo("invalidated-hash");
        assertThat(user.getDeletedAt()).isEqualTo(withdrawnAt);
    }
}
