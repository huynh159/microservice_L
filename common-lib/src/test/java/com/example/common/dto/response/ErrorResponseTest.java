package com.example.common.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void testErrorResponseBuilderAndJsonSerialization() throws Exception {
        Instant now = Instant.parse("2026-10-05T07:30:00Z");
        ErrorDetail detail = ErrorDetail.builder()
                .field("email")
                .message("đã tồn tại")
                .build();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(now)
                .status(409)
                .error("CONFLICT")
                .code("USER_EMAIL_EXISTS")
                .message("Email đã được sử dụng")
                .path("/api/v1/users")
                .traceId("a1b2c3")
                .details(List.of(detail))
                .build();

        assertThat(errorResponse.getStatus()).isEqualTo(409);
        assertThat(errorResponse.getError()).isEqualTo("CONFLICT");
        assertThat(errorResponse.getCode()).isEqualTo("USER_EMAIL_EXISTS");
        assertThat(errorResponse.getMessage()).isEqualTo("Email đã được sử dụng");
        assertThat(errorResponse.getPath()).isEqualTo("/api/v1/users");
        assertThat(errorResponse.getTraceId()).isEqualTo("a1b2c3");
        assertThat(errorResponse.getDetails()).hasSize(1);
        assertThat(errorResponse.getDetails().get(0).getField()).isEqualTo("email");

        String json = objectMapper.writeValueAsString(errorResponse);
        assertThat(json).contains("\"status\":409");
        assertThat(json).contains("\"code\":\"USER_EMAIL_EXISTS\"");
        assertThat(json).contains("\"traceId\":\"a1b2c3\"");
    }
}
