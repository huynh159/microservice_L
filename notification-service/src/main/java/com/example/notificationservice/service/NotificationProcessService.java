package com.example.notificationservice.service;

import com.example.common.dto.event.UserCreatedEvent;

public interface NotificationProcessService {
    /**
     * Xử lý sự kiện UserCreatedEvent một cách Idempotent (chống trùng lặp).
     *
     * @param eventId Mã định danh duy nhất của sự kiện
     * @param event Dữ liệu payload của UserCreatedEvent
     * @return true nếu sự kiện được xử lý và gửi email thành công; false nếu sự kiện đã được xử lý trước đó (bị bỏ qua)
     */
    boolean processUserCreatedEvent(String eventId, UserCreatedEvent event);
}
