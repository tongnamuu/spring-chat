package com.example.chat.api.dto;

import com.example.chat.core.entity.UserEntity;

public record SignupResponse(Long userId, String username, String nickname) {

    public static SignupResponse from(UserEntity user) {
        return new SignupResponse(user.getUserId(), user.getUsername(), user.getNickname());
    }
}
