package com.example.chatservice.entity;

/**
 * Trạng thái của phòng chat:
 * - OPEN: Phòng đang mở, khách hàng và admin có thể nhắn tin cho nhau.
 * - RESOLVED: Admin đã giải quyết xong vấn đề cho khách.
 * - CLOSED: Cuộc trò chuyện đã kết thúc, đóng lại.
 */
public enum RoomStatus {
    OPEN,
    RESOLVED,
    CLOSED
}
