package com.example.chat.common.dto;

import jakarta.validation.constraints.NotBlank;

public class JoinRoomRequest {
    @NotBlank(message = "Invite code is required")
    private String inviteCode;

    public JoinRoomRequest() {}

    public JoinRoomRequest(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public static class Builder {
        private String inviteCode;

        public Builder inviteCode(String inviteCode) {
            this.inviteCode = inviteCode;
            return this;
        }

        public JoinRoomRequest build() {
            return new JoinRoomRequest(inviteCode);
        }
    }
}
