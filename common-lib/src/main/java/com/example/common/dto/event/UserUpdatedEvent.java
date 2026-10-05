package com.example.common.dto.event;

import java.util.UUID;

public class UserUpdatedEvent {
    private UUID userId;
    private String email;
    private String fullName;

    public UserUpdatedEvent() {
    }

    public UserUpdatedEvent(UUID userId, String email, String fullName) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public static class Builder {
        private UUID userId;
        private String email;
        private String fullName;

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public UserUpdatedEvent build() {
            return new UserUpdatedEvent(userId, email, fullName);
        }
    }
}
