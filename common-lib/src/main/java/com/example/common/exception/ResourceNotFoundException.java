package com.example.common.exception;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }

    public ResourceNotFoundException(String code, String message) {
        super(404, code, message);
    }
}
