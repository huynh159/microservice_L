package com.example.chatservice.client;

import com.example.chatservice.dto.response.UserSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * ==============================================================================
 * GIAO TIẾP ĐỒNG BỘ GIỮA CÁC MICROSERVICES (INTER-SERVICE COMMUNICATION)
 * ------------------------------------------------------------------------------
 * KIẾN THỨC KHI ĐI PHỎNG VẤN & ĐI LÀM:
 * 1. RestClient trong Spring Boot 3.x:
 *    - Là HTTP Client dạng Fluent API hiện đại nhất của Spring Framework.
 *    - Thay thế cho RestTemplate cũ kỹ và nhẹ hơn WebClient (không bắt buộc kéo WebFlux).
 *
 * 2. Kỹ thuật "Token Relay" (Chuyển tiếp Token):
 *    - Khi Admin mở xem chi tiết một khách hàng trong phòng chat, chat-service cần
 *      gọi sang user-service để lấy thêm: họ tên đầy đủ, avatar, số điện thoại.
 *    - Do API xem chi tiết người dùng bên user-service (/api/v1/users/{id}) yêu cầu quyền ADMIN,
 *      chat-service sẽ TRUYỀN TIẾP (Relay) chính chiếc Bearer Token của Admin sang user-service.
 *    - user-service nhận được token -> xác minh đúng là Admin -> trả về dữ liệu người dùng.
 *
 * 3. Chống lỗi dây chuyền (Resilience & Cascading Failure):
 *    - Nếu user-service bị nghẽn mạng, sập nguồn hoặc timeout:
 *    - chat-service BẮT BUỘC phải try-catch hoặc dùng Circuit Breaker (Resilience4j).
 *    - Nếu lỗi, trả về Optional.empty() và hiển thị tên fallback (ví dụ: email khách hàng),
 *      TUYỆT ĐỐI KHÔNG ĐƯỢC để sập luôn cả hệ thống chat của người dùng!
 * ==============================================================================
 */
@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestClient restClient;

    public UserServiceClient(
            RestClient.Builder builder,
            @Value("${services.user-service.url:http://localhost:8081}") String userServiceUrl
    ) {
        this.restClient = builder
                .baseUrl(userServiceUrl)
                .build();
    }

    /**
     * Gọi HTTP GET sang user-service để lấy thông tin chi tiết người dùng
     *
     * @param userId      ID người dùng cần tra cứu
     * @param bearerToken Token của người dùng hoặc Admin đang đăng nhập (Token Relay)
     * @return UserSummaryResponse nếu thành công, hoặc Optional.empty() nếu lỗi/không tìm thấy
     */
    public Optional<UserSummaryResponse> getUserById(UUID userId, String bearerToken) {
        if (userId == null) {
            return Optional.empty();
        }

        try {
            log.info("Gọi sang user-service để lấy thông tin chi tiết của userId [{}]", userId);

            UserSummaryResponse response = restClient.get()
                    .uri("/api/v1/users/{id}", userId)
                    // Token Relay: Chuyển tiếp Token xác thực sang user-service
                    .header("Authorization", bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        log.warn("user-service trả về lỗi client [{}] cho userId [{}]", resp.getStatusCode(), userId);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        log.error("user-service gặp sự cố server [{}] khi lấy thông tin userId [{}]", resp.getStatusCode(), userId);
                    })
                    .body(UserSummaryResponse.class);

            return Optional.ofNullable(response);

        } catch (Exception ex) {
            // TÍNH CHỊU LỖI (Graceful Degradation): Không làm sập luồng chat nếu user-service không phản hồi
            log.warn("Không thể kết nối đến user-service cho userId [{}]: {}. Sử dụng thông tin mặc định.",
                    userId, ex.getMessage());
            return Optional.empty();
        }
    }
}
