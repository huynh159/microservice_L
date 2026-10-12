package com.example.chatservice.dto.request;

import com.example.chatservice.entity.RoomStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateRoomStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private RoomStatus status;

    public UpdateRoomStatusRequest() {
    }

    public UpdateRoomStatusRequest(RoomStatus status) {
        this.status = status;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }
}
