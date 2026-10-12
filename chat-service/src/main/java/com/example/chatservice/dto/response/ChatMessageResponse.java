package com.example.chatservice.dto.response;

import com.example.chatservice.entity.SenderRole;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO dữ liệu tin nhắn trả về cho Client hoặc phát qua WebSocket
 */
public class ChatMessageResponse {

    private UUID id;
    private UUID roomId;
    private UUID senderId;
    private SenderRole senderRole;
    private String content;
    private Instant createdAt;
    private boolean isRead;

    public ChatMessageResponse() {
    }

    public ChatMessageResponse(UUID id, UUID roomId, UUID senderId, SenderRole senderRole, String content, Instant createdAt, boolean isRead) {
        this.id = id;
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.content = content;
        this.createdAt = createdAt;
        this.isRead = isRead;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID roomId;
        private UUID senderId;
        private SenderRole senderRole;
        private String content;
        private Instant createdAt;
        private boolean isRead;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder roomId(UUID roomId) {
            this.roomId = roomId;
            return this;
        }

        public Builder senderId(UUID senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder senderRole(SenderRole senderRole) {
            this.senderRole = senderRole;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder isRead(boolean isRead) {
            this.isRead = isRead;
            return this;
        }

        public ChatMessageResponse build() {
            return new ChatMessageResponse(id, roomId, senderId, senderRole, content, createdAt, isRead);
        }
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public void setSenderId(UUID senderId) {
        this.senderId = senderId;
    }

    public SenderRole getSenderRole() {
        return senderRole;
    }

    public void setSenderRole(SenderRole senderRole) {
        this.senderRole = senderRole;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}
