package com.example.chat.common.dto;

import com.example.chat.common.enums.Role;

public record RoomMemberDto(Long userId, String nickname, Role role) {}
