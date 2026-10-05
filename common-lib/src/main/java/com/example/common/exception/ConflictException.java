package com.example.common.exception;

public class ConflictException extends BusinessException {
    public ConflictException(String message) {
        super(409, "CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(409, code, message);
    }
}
