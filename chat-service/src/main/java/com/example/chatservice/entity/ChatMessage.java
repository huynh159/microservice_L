package com.example.chatservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * ==============================================================================
 * ENTITY: CHAT MESSAGE (TIN NHẮN TRONG PHÒNG CHAT)
 * ------------------------------------------------------------------------------
 * Lưu trữ nội dung tin nhắn, thời gian gửi, người gửi (User hoặc Admin),
 * và trạng thái đã đọc hay chưa.
 * ==============================================================================
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    /**
     * ID của người gửi (có thể là userId của khách hoặc adminId của quản trị viên)
     */
    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    /**
     * Vai trò của người gửi: ROLE_USER hoặc ROLE_ADMIN
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 20)
    private SenderRole senderRole;

    /**
     * Nội dung tin nhắn dạng văn bản
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public ChatMessage() {
    }

    public ChatMessage(ChatRoom room, UUID senderId, SenderRole senderRole, String content) {
        this.room = room;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.content = content;
        this.isRead = false;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ChatRoom getRoom() {
        return room;
    }

    public void setRoom(ChatRoom room) {
        this.room = room;
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
