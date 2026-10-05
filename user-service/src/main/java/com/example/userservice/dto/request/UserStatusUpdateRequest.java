package com.example.userservice.dto.request;

import com.example.userservice.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public class UserStatusUpdateRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private UserStatus status;

    public UserStatusUpdateRequest() {
    }

    public UserStatusUpdateRequest(UserStatus status) {
        this.status = status;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }
}
