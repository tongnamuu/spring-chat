package com.example.chat.core.entity;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserEntityTest {

    @Test
    void requiresPasswordHash() {
        assertThatThrownBy(() -> UserEntity.builder()
                .username("alice")
                .nickname("Alice")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password hash is required");

        assertThatThrownBy(() -> UserEntity.builder()
                .username("alice")
                .nickname("Alice")
                .passwordHash(" ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password hash is required");
    }

    @Test
    void withdrawalAnonymizesIdentityAndIsIdempotent() {
        UserEntity user = UserEntity.builder()
                .userId(42L)
                .username("alice")
                .nickname("Alice")
                .passwordHash("original-hash")
                .build();
        ZonedDateTime withdrawnAt = ZonedDateTime.parse("2026-08-09T10:00:00+09:00");

        user.withdraw("invalidated-hash", withdrawnAt);
        user.withdraw("second-hash", withdrawnAt.plusHours(1));

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getUsername()).isEqualTo("withdrawn-42");
        assertThat(user.getNickname()).isEqualTo(UserEntity.WITHDRAWN_NICKNAME);
        assertThat(user.getPasswordHash()).isEqualTo("invalidated-hash");
        assertThat(user.getDeletedAt()).isEqualTo(withdrawnAt);
    }
}
