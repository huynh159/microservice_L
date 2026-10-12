package com.example.userservice.service;

import com.example.userservice.entity.OutboxEvent;

public interface OutboxService {

    /**
     * Đóng gói sự kiện vào EventEnvelope chuẩn và lưu vào bảng outbox_events
     * trong cùng Transaction của nghiệp vụ người dùng.
     *
     * @param aggregateType Loại thực thể (ví dụ: "USER")
     * @param aggregateId   Định danh của thực thể (ví dụ: userId)
     * @param eventType     Tên loại sự kiện (ví dụ: "UserCreatedEvent")
     * @param eventPayload  Dữ liệu nghiệp vụ của sự kiện (không chứa mật khẩu/token)
     * @return Bản ghi OutboxEvent đã được lưu vào Database
     */
    <T> OutboxEvent saveEvent(String aggregateType, String aggregateId, String eventType, T eventPayload);
}
