package com.example.chatservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload gửi tin nhắn từ Client (qua WebSocket hoặc REST API)
 */
public class SendMessageRequest {

    /**
     * ID phòng chat muốn gửi tin nhắn vào
     */
    private UUID roomId;

    /**
     * Nội dung tin nhắn
     */
    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Size(max = 2000, message = "Tin nhắn không được vượt quá 2000 ký tự")
    private String content;

    public SendMessageRequest() {
    }

    public SendMessageRequest(UUID roomId, String content) {
        this.roomId = roomId;
        this.content = content;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
