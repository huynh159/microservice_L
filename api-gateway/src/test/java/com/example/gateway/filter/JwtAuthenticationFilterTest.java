package com.example.gateway.filter;

import com.example.gateway.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Kiểm thử Bộ lọc Xác thực JWT tại Gateway (JwtAuthenticationFilterTest)")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    private JwtAuthenticationFilter authFilter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        authFilter = new JwtAuthenticationFilter(jwtService, objectMapper);
    }

    @Test
    @DisplayName("Endpoint Public (POST /api/v1/auth/login) được phép đi qua mà không cần Token")
    void shouldAllowPublicEndpointWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.POST, "/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(authFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled.get()).isTrue();
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Endpoint Protected (GET /api/v1/users/me) nếu thiếu Header Authorization thì bị từ chối 401")
    void shouldRejectProtectedEndpointWhenAuthHeaderMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, "/api/v1/users/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(authFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Endpoint Protected với Token không hợp lệ thì bị từ chối 401")
    void shouldRejectProtectedEndpointWhenTokenInvalid() {
        String invalidToken = "invalid.token.here";
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, "/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.validateToken(invalidToken)).thenReturn(false);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(authFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Endpoint Protected với Token hợp lệ thì được cấp phép và làm giàu Headers (X-User-Id, X-User-Email, X-User-Roles)")
    void shouldAllowAndEnrichHeadersWhenTokenValid() {
        String validToken = "valid.jwt.token";
        String userId = "00000000-0000-0000-0000-000000000001";
        String email = "john@example.com";
        Set<String> roles = Set.of("ROLE_USER");

        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, "/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.validateToken(validToken)).thenReturn(true);
        when(jwtService.extractUserId(validToken)).thenReturn(userId);
        when(jwtService.extractEmail(validToken)).thenReturn(email);
        when(jwtService.extractRoles(validToken)).thenReturn(roles);

        AtomicReference<String> downstreamUserId = new AtomicReference<>();
        AtomicReference<String> downstreamEmail = new AtomicReference<>();
        AtomicReference<String> downstreamRoles = new AtomicReference<>();

        GatewayFilterChain chain = mutatedExchange -> {
            downstreamUserId.set(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
            downstreamEmail.set(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Email"));
            downstreamRoles.set(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Roles"));
            return Mono.empty();
        };

        StepVerifier.create(authFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamUserId.get()).isEqualTo(userId);
        assertThat(downstreamEmail.get()).isEqualTo(email);
        assertThat(downstreamRoles.get()).isEqualTo("ROLE_USER");
    }
}
