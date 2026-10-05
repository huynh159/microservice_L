package com.example.common.exception;

public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super(403, "FORBIDDEN", message);
    }

    public ForbiddenException(String code, String message) {
        super(403, code, message);
    }
}
