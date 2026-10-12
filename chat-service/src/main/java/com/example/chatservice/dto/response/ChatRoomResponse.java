package com.example.chatservice.dto.response;

import com.example.chatservice.entity.RoomStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO đại diện cho một phiên phòng chat
 */
public class ChatRoomResponse {

    private UUID id;
    private UUID userId;
    private String userEmail;
    private String userFullName;
    private UUID adminId;
    private RoomStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private String lastMessage;
    private Instant lastMessageTime;

    public ChatRoomResponse() {
    }

    public ChatRoomResponse(UUID id, UUID userId, String userEmail, String userFullName, UUID adminId, RoomStatus status, Instant createdAt, Instant updatedAt, String lastMessage, Instant lastMessageTime) {
        this.id = id;
        this.userId = userId;
        this.userEmail = userEmail;
        this.userFullName = userFullName;
        this.adminId = adminId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID userId;
        private String userEmail;
        private String userFullName;
        private UUID adminId;
        private RoomStatus status;
        private Instant createdAt;
        private Instant updatedAt;
        private String lastMessage;
        private Instant lastMessageTime;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public Builder userEmail(String userEmail) {
            this.userEmail = userEmail;
            return this;
        }

        public Builder userFullName(String userFullName) {
            this.userFullName = userFullName;
            return this;
        }

        public Builder adminId(UUID adminId) {
            this.adminId = adminId;
            return this;
        }

        public Builder status(RoomStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder lastMessage(String lastMessage) {
            this.lastMessage = lastMessage;
            return this;
        }

        public Builder lastMessageTime(Instant lastMessageTime) {
            this.lastMessageTime = lastMessageTime;
            return this;
        }

        public ChatRoomResponse build() {
            return new ChatRoomResponse(id, userId, userEmail, userFullName, adminId, status, createdAt, updatedAt, lastMessage, lastMessageTime);
        }
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public void setAdminId(UUID adminId) {
        this.adminId = adminId;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Instant getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Instant lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }
}
