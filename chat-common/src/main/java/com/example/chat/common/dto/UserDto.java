package com.example.chat.common.dto;

public class UserDto {
    private Long userId;
    private String nickname;

    public UserDto() {}

    public UserDto(Long userId, String nickname) {
        this.userId = userId;
        this.nickname = nickname;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public static class Builder {
        private Long userId;
        private String nickname;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder nickname(String nickname) { this.nickname = nickname; return this; }

        public UserDto build() {
            return new UserDto(userId, nickname);
        }
    }
}
