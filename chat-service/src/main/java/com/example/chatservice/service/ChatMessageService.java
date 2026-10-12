package com.example.chatservice.service;

import com.example.chatservice.dto.request.SendMessageRequest;
import com.example.chatservice.dto.response.ChatMessageResponse;
import com.example.chatservice.entity.SenderRole;

import java.util.List;
import java.util.UUID;

public interface ChatMessageService {

    /**
     * Gửi tin nhắn mới vào phòng chat:
     * - Lưu tin nhắn vào PostgreSQL
     * - Đẩy realtime qua WebSocket SimpMessagingTemplate tới mọi người tham gia phòng
     */
    ChatMessageResponse sendMessage(UUID senderId, SenderRole senderRole, SendMessageRequest request);

    /**
     * Lấy toàn bộ lịch sử tin nhắn trong phòng chat theo thứ tự thời gian
     */
    List<ChatMessageResponse> getMessagesByRoomId(UUID roomId, UUID currentUserId, boolean isAdmin);
}
