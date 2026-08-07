package com.example.chat.common.exception;

public enum ErrorCode {
    USER_NOT_FOUND("U001", "User not found"),
    ROOM_NOT_FOUND("R001", "Chat room not found"),
    ROOM_FULL("R002", "Chat room exceeds maximum capacity (50)"),
    ALREADY_JOINED("R003", "User has already joined this room"),
    NOT_ROOM_MEMBER("R004", "User is not a member of this chat room"),
    INVALID_INVITE_CODE("R005", "Invalid invitation code"),
    INVALID_ROOM_CAPACITY("R006", "Capacity must be between 2 and 50"),
    DIRECT_ROOM_INVALID("R007", "Direct chat room capacity must be exactly 2"),
    INTERNAL_SERVER_ERROR("S001", "Internal server error");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
