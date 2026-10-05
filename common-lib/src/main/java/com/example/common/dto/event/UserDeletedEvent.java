package com.example.common.dto.event;

import java.util.UUID;

public class UserDeletedEvent {
    private UUID userId;

    public UserDeletedEvent() {
    }

    public UserDeletedEvent(UUID userId) {
        this.userId = userId;
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

    public static class Builder {
        private UUID userId;

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public UserDeletedEvent build() {
            return new UserDeletedEvent(userId);
        }
    }
}
