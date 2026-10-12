package com.example.chatservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * ==============================================================================
 * ENTITY: CHAT ROOM (PHÒNG CHAT 1-1 GIỮA USER VÀ ADMIN)
 * ------------------------------------------------------------------------------
 * Mỗi khách hàng sẽ có một hoặc nhiều phiên hỗ trợ.
 * Admin có thể tiếp nhận (assign) phòng chat này để trực tiếp giải đáp.
 * ==============================================================================
 */
@Entity
@Table(name = "chat_rooms")
public class ChatRoom {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * ID của người dùng (khách hàng) yêu cầu hỗ trợ (lấy từ JWT token)
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Email của người dùng (lưu sẵn để Admin xem danh sách phòng mà không cần gọi user-service)
     */
    @Column(name = "user_email", nullable = false)
    private String userEmail;

    /**
     * ID của Admin tiếp nhận hỗ trợ (NULL nếu phòng đang nằm trong hàng đợi chờ Admin bấm nhận)
     */
    @Column(name = "admin_id")
    private UUID adminId;

    /**
     * Trạng thái phòng (OPEN, RESOLVED, CLOSED)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomStatus status = RoomStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = RoomStatus.OPEN;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public ChatRoom() {
    }

    public ChatRoom(UUID userId, String userEmail) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.status = RoomStatus.OPEN;
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
}
