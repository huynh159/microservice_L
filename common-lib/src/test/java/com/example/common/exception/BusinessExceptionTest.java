package com.example.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void testResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");
        assertThat(ex.getStatus()).isEqualTo(404);
        assertThat(ex.getCode()).isEqualTo("NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("User not found");
    }

    @Test
    void testConflictException() {
        ConflictException ex = new ConflictException("USER_EMAIL_EXISTS", "Email already in use");
        assertThat(ex.getStatus()).isEqualTo(409);
        assertThat(ex.getCode()).isEqualTo("USER_EMAIL_EXISTS");
        assertThat(ex.getMessage()).isEqualTo("Email already in use");
    }

    @Test
    void testBadRequestException() {
        BadRequestException ex = new BadRequestException("Invalid input");
        assertThat(ex.getStatus()).isEqualTo(400);
        assertThat(ex.getCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void testUnauthorizedException() {
        UnauthorizedException ex = new UnauthorizedException("Invalid credentials");
        assertThat(ex.getStatus()).isEqualTo(401);
        assertThat(ex.getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void testForbiddenException() {
        ForbiddenException ex = new ForbiddenException("Access denied");
        assertThat(ex.getStatus()).isEqualTo(403);
        assertThat(ex.getCode()).isEqualTo("FORBIDDEN");
    }
}
