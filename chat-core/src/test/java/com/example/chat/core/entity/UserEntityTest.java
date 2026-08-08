package com.example.chat.core.entity;

import org.junit.jupiter.api.Test;

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
}
