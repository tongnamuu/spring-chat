package com.example.chat.common.dto;

public class UserDto {
    private Long userId;
    private String username;
    private String nickname;

    public UserDto() {}

    public UserDto(Long userId, String username, String nickname) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public static class Builder {
        private Long userId;
        private String username;
        private String nickname;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder nickname(String nickname) { this.nickname = nickname; return this; }

        public UserDto build() {
            return new UserDto(userId, username, nickname);
        }
    }
}
