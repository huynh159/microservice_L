package com.example.chatservice.entity;

/**
 * Vai trò của người gửi tin nhắn trong phòng chat:
 * - ROLE_USER: Khách hàng cần hỗ trợ
 * - ROLE_ADMIN: Quản trị viên / Nhân viên chăm sóc khách hàng
 */
public enum SenderRole {
    ROLE_USER,
    ROLE_ADMIN
}
