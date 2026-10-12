package com.example.userservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ==============================================================================
 * THỰC THỂ OUTBOX EVENT (TRANSACTIONAL OUTBOX PATTERN) - BƯỚC 3.7
 * ------------------------------------------------------------------------------
 * KIẾN THỨC CỐT LÕI:
 * 1. Dual-Write Problem (Vấn đề ghi vào 2 hệ thống độc lập):
 *    - Nếu vừa lưu User vào Database, vừa bắn tin nhắn lên Kafka:
 *      * Nếu lưu DB xong nhưng Kafka sập -> Mất tin nhắn, notification-service không biết có user mới!
 *      * Nếu bắn Kafka xong nhưng commit DB bị rollback -> Notification gửi email chào mừng nhưng user không tồn tại!
 * 2. Giải pháp Transactional Outbox Pattern:
 *    - Khi tạo/sửa/xóa User, bản ghi sự kiện Outbox được INSERT vào bảng `outbox_events`
 *      TRONG CÙNG MỘT DATABASE TRANSACTION với bảng `users`.
 *    - Do chung transaction, hoặc cả hai cùng thành công, hoặc cả hai cùng rollback (tính nguyên tử ACID).
 * 3. Bộ quét nền (Scheduled Worker):
 *    - Đọc các bản ghi chưa phát (`published_at IS NULL`) -> đẩy lên Kafka -> đánh dấu `published_at = now()`.
 *    - Sử dụng `FOR UPDATE SKIP LOCKED` để an toàn khi chạy nhiều container đồng thời.
 * ==============================================================================
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_events_unpublished", columnList = "created_at, published_at"),
        @Index(name = "idx_outbox_events_aggregate", columnList = "aggregate_type, aggregate_id")
})
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markAsPublished() {
        this.publishedAt = Instant.now();
    }

    // =========================================================================
    // GETTERS & SETTERS
    // =========================================================================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutboxEvent that = (OutboxEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
