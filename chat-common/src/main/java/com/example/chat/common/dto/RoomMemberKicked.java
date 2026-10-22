package com.example.chat.common.dto;

public record RoomMemberKicked(Long roomId, Long userId, String username) {
    public static final String CHANNEL = "spring-chat:room-member-kicked";
}
