package com.example.chat.api.dto;

import com.example.chat.core.service.UserRegistrationResult;

public record SignupResponse(Long userId, String nickname) {

    public static SignupResponse from(UserRegistrationResult result) {
        return new SignupResponse(result.userId(), result.nickname());
    }
}
