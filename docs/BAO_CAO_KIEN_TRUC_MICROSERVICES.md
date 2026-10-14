# 📑 BÁO CÁO KỸ THUẬT CHI TIẾT DỰ ÁN NỀN TẢNG MICROSERVICES
**Dự án:** Event-Driven Microservices Platform (User, Notification & Realtime Chat)  
**Công nghệ:** Spring Boot 3.4.3, Java 21 LTS, Apache Kafka, PostgreSQL 16, Redis 7, Docker  
**Người thực hiện:** Kỹ sư phần mềm / Học viên dự án  
**Ngày lập:** 14/10/2026  

---

## MỤC LỤC BÁO CÁO
1. [Tổng quan Dự án & Kiến trúc Hệ thống](#1-tổng-quan-dự-án--kiến-trúc-hệ-thống)
2. [Các Mẫu Thiết Kế Kiến Trúc Nâng Cao (Architecture Patterns)](#2-các-mẫu-thiết-kế-kiến-trúc-nâng-cao-architecture-patterns)
3. [Hạ Tầng Nhắn Tin Bất Đồng Bộ Với Apache Kafka](#3-hạ-tầng-nhắn-tin-bất-đồng-bộ-với-apache-kafka)
4. [Bảo Mật Hệ Thống & Quản Trị Danh Tính (Security & Authentication)](#4-bảo-mật-hệ-thống--quản-trị-danh-tính-security--authentication)
5. [Tối Ưu Hiệu Năng & Bộ Đệm Với Redis 7](#5-tối-ưu-hiệu-năng--bộ-đệm-với-redis-7)
6. [Quản Trị Cơ Sở Dữ Liệu Phân Tán & Flyway](#6-quản-trị-cơ-sở-dữ-liệu-phân-tán--flyway)
7. [Giao Tiếp Thời Gian Thực Với WebSocket STOMP](#7-giao-tiếp-thời-gian-thực-với-websocket-stomp)
8. [Khả Năng Quan Sát (Observability) & Distributed Tracing](#8-khả-năng-quan-sát-observability--distributed-tracing)
9. [Chiến Lược Kiểm Thử (Testing Strategy & E2E)](#9-chiến-lược-kiểm-thử-testing-strategy--e2e)
10. [Tổng Kết Đánh Giá & Hướng Mở Rộng](#10-tổng-kết-đánh-giá--hướng-mở-rộng)

---

## 1. TỔNG QUAN DỰ ÁN & KIẾN TRÚC HỆ THỐNG

### 1.1 Mục tiêu dự án
Dự án được xây dựng nhằm giải quyết bài toán của các hệ thống nguyên khối (Monolith) truyền thống khi mở rộng quy mô: nghẽn cổ chai cơ sở dữ liệu, dịch vụ phụ làm chậm dịch vụ chính, và khó khăn khi triển khai độc lập.

Hệ thống được thiết kế theo chuẩn **Microservices hướng sự kiện (Event-Driven Architecture - EDA)**, tách rời hoàn toàn thành các dịch vụ độc lập với cơ sở dữ liệu riêng biệt (*Database-per-Service*).

### 1.2 Phân rã Microservices
1. **`common-lib`**: Thư viện lõi dùng chung, chứa:
   * Vỏ bọc sự kiện chuẩn hóa `EventEnvelope<T>`.
   * Các DTO sự kiện: `UserCreatedEvent`, `UserUpdatedEvent`, `UserDeletedEvent`.
   * Định dạng chuẩn lỗi `ErrorResponse` và kết quả phân trang `PageResponse<T>`.
   * Bộ ngoại lệ nghiệp vụ thống nhất (`BusinessException`, `ResourceNotFoundException`, `ConflictException`).
2. **`user-service` (Port 8081)**:
   * Quản lý vòng đời người dùng, hồ sơ cá nhân.
   * Xác thực và phân quyền RBAC (Role-Based Access Control) với Spring Security & JWT.
   * Bộ đệm Redis cho thông tin cá nhân và chống tấn công Brute-force đăng nhập.
   * Triển khai mẫu thiết kế **Transactional Outbox** để bảo đảm an toàn dữ liệu khi phát sự kiện lên Kafka.
3. **`notification-service` (Port 8082)**:
   * Lắng nghe sự kiện người dùng từ Kafka topic `user-events`.
   * Xử lý chống trùng lặp (**Idempotent Consumer**) bảo đảm không gửi email 2 lần cho khách hàng.
   * Tự động phục hồi lỗi với **Exponential Backoff Retry** và cách ly tin nhắn lỗi qua **Dead Letter Topic (DLT)**.
   * Gửi email chào mừng bằng giao thức SMTP tới Mailpit Mock Server.
4. **`chat-service` (Port 8083)**:
   * Quản lý phòng chat hỗ trợ và lịch sử tin nhắn.
   * Giao tiếp hai chiều thời gian thực qua **WebSocket STOMP** (`/ws-chat`).
   * Xác thực quyền truy cập ngay tại kênh kết nối WebSocket qua `ChannelInterceptor`.
   * Giao tiếp liên dịch vụ (Inter-service REST call) sang `user-service` với kỹ thuật **Token Relay**.

### 1.3 Sơ đồ kiến trúc phân tầng (System Architecture)
```
[ Client / Web / Mobile / Postman ]
                │
         HTTPS REST / WebSocket
                ▼
   ┌───────────────────────────────┐
   │         user-service          │ ────▶ PostgreSQL (user_db :5433)
   │          (Port 8081)          │ ────▶ Redis Cache & Rate-limit (:6379)
   └───────────────┬───────────────┘
                   │  (Transactional Outbox - Local DB Insert)
                   ▼
       [ Apache Kafka (KRaft :9092) ]
                   │
         topic: 'user-events'
                   │
                   ▼
   ┌───────────────────────────────┐
   │     notification-service      │ ────▶ PostgreSQL (notification_db :5433)
   │          (Port 8082)          │ ────▶ Mailpit SMTP Mock Email (:1025)
   └───────────────┬───────────────┘
                   │ (Poison Message Fallback)
                   ▼
      [ topic: 'user-events.DLT' ]

   ┌───────────────────────────────┐
   │         chat-service          │ ────▶ PostgreSQL (chat_db :5433)
   │   (Port 8083 - STOMP /ws/chat)│ ────▶ Inter-service REST sang user-service (Token Relay)
   └───────────────────────────────┘
```

---

## 2. CÁC MẪU THIẾT KẾ KIẾN TRÚC NÂNG CAO (ARCHITECTURE PATTERNS)

### 2.1 Transactional Outbox Pattern
* **Vấn đề giải quyết (Dual-Write Problem):** Trong hệ thống phân tán, nếu vừa ghi vào Database vừa gọi lệnh `kafkaTemplate.send()` trực tiếp, khi gặp sự cố mạng tới Kafka hoặc ứng dụng bị crash giữa chừng, dữ liệu trong Database đã lưu nhưng sự kiện không bao giờ được gửi đi. Dữ liệu giữa các service sẽ bị mất đồng bộ vĩnh viễn.
* **Cách triển khai thực tế:**
  1. Trong `UserServiceImpl.register()`: Thao tác `userRepository.save(user)` và `outboxService.saveEvent(...)` được gom chung trong **CÙNG 1 TRANSACTION DATABASE** (`@Transactional`).
  2. Bảng `outbox_events` lưu bản ghi sự kiện ở dạng JSON (đã bọc trong `EventEnvelope`) với `published_at = NULL`.
  3. Lớp `OutboxEventProcessor` chạy ngầm định kỳ (`@Scheduled` mỗi 2s):
     * Sử dụng câu lệnh native query: `SELECT ... FOR UPDATE SKIP LOCKED` để đọc tối đa 50 bản ghi chưa phát. Cơ chế `SKIP LOCKED` cho phép chạy nhiều bản sao (replica) của `user-service` song song mà không bị tranh chấp khóa hay xử lý trùng lặp.
     * Dùng `kafkaTemplate.send(...).get(5, TimeUnit.SECONDS)` để gửi đồng bộ lên Kafka.
     * Khi Kafka xác nhận thành công (ACK), đánh dấu `event.markAsPublished()` và lưu lại DB.
     * Nếu Kafka sập, tiến trình `break` ngay lập tức để bảo vệ CPU và thử lại ở chu kỳ tiếp theo mà không mất dữ liệu.

### 2.2 Idempotent Consumer Pattern (Chống Xử Lý Trùng Lặp)
* **Vấn đề giải quyết:** Do Kafka sử dụng cơ chế bảo đảm phân phối *At-least-once*, khi mạng chập chờn, Consumer có thể nhận cùng một bản tin 2 lần. Nếu không xử lý, khách hàng sẽ nhận được 2 email chào mừng gây trải nghiệm xấu.
* **Cách triển khai thực tế:**
  1. Mỗi sự kiện mang một mã duy nhất `eventId` dạng UUID từ phía Producer.
  2. Tại `NotificationProcessServiceImpl`:
     * Trước khi gửi mail, gọi `processedEventRepository.existsById(eventId)`.
     * Nếu đã tồn tại ➔ Lập tức bỏ qua (trả về `false`), không gửi email.
     * Nếu chưa tồn tại ➔ Soạn gửi mail qua SMTP ➔ Ghi `new ProcessedEvent(eventId, now)` vào DB trong cùng transaction.

---

## 3. HẠ TẦNG NHẮN TIN BẤT ĐỒNG BỘ VỚI APACHE KAFKA

### 3.1 Cấu trúc Topic & Phân vùng (Partitioning Strategy)
* **Topic:** `user-events` (3 Partitions, Replication factor = 1 trong môi trường Dev).
* **Định tuyến phân vùng bằng Message Key:**
  * Producer luôn truyền `aggregateId` (chính là `userId`) làm Message Key khi gọi `kafkaTemplate.send(topic, key, payload)`.
  * **Ý nghĩa:** Thuật toán băm của Kafka bảo đảm mọi sự kiện của **CÙNG MỘT NGƯỜI DÙNG** sẽ luôn rơi vào **CÙNG MỘT PARTITION**, bảo toàn thứ tự nghiệp vụ (Sự kiện Tạo luôn được xử lý trước sự kiện Sửa hoặc Xóa).

### 3.2 Khả năng chịu lỗi: Dead Letter Topic (DLT) & Exponential Backoff
Trong file cấu hình `KafkaConsumerConfig`, cơ chế xử lý lỗi được thiết lập qua `DefaultErrorHandler`:
1. **Exponential Backoff:** Khi gặp lỗi tạm thời (lỗi mạng hoặc máy chủ SMTP bận), hệ thống thử lại tối đa 3 lần với khoảng cách thời gian giãn cách theo cấp số nhân:
   $$\text{Interval}_1 = 500\text{ms} \quad\longrightarrow\quad \text{Interval}_2 = 1000\text{ms} \quad\longrightarrow\quad \text{Interval}_3 = 2000\text{ms}$$
2. **Phân loại lỗi nghiêm trọng (Non-retryable):** Các ngoại lệ về cấu trúc JSON (`JsonParseException`, `JsonMappingException`, `IllegalArgumentException`) được đánh dấu không thể tự khỏi ➔ Không lãng phí thời gian thử lại.
3. **Dead Letter Topic Recoverer:** Khi thử lại 3 lần vẫn thất bại hoặc gặp lỗi nghiêm trọng, `DeadLetterPublishingRecoverer` tự động chuyển tiếp bản tin vào topic `user-events.DLT` kèm thông tin nguyên nhân lỗi trong Kafka Header để phục vụ điều tra sau sự cố.

---

## 4. BẢO MẬT HỆ THỐNG & QUẢN TRỊ DANH TÍNH (SECURITY & AUTHENTICATION)

### 4.1 Kiến trúc Stateless JWT & Refresh Token Rotation
Dự án áp dụng mô hình bảo mật lai (Hybrid Authentication):

| Thuộc tính | Access Token | Refresh Token |
| :--- | :--- | :--- |
| **Bản chất** | Chuỗi JWT chuẩn RFC 7519 (Self-contained) | Chuỗi Hex ngẫu nhiên 64 ký tự (Opaque String) |
| **Thời hạn** | Ngắn hạn: **15 phút** | Dài hạn: **7 ngày** |
| **Lưu trữ phía Server**| **HOÀN TOÀN KHÔNG LƯU** (Stateless 100%) | Lưu giá trị băm **SHA-256** vào bảng `refresh_tokens` |
| **Cơ chế xác thực** | CPU giải mã chữ ký HMAC-SHA256 với `JWT_SECRET` | Truy vấn cơ sở dữ liệu kiểm tra tính hợp lệ và thu hồi |

### 4.2 Cơ chế xoay vòng Refresh Token (Token Rotation)
1. Khi Access Token hết hạn, Client gửi Refresh Token lên endpoint `POST /api/v1/auth/refresh`.
2. Server băm SHA-256 token gửi lên và kiểm tra trong DB:
   * Nếu token đã bị thu hồi (`revoked_at != NULL`): Hệ thống phát hiện nghi vấn **Token bị đánh cắp** ➔ Lập tức từ chối (`401 Unauthorized`) và có thể kích hoạt khóa toàn bộ token liên quan.
   * Nếu hợp lệ: Đánh dấu token cũ là ĐÃ THU HỒI (`revoked_at = NOW()`), sinh cặp Access Token và Refresh Token mới trả về cho Client.

### 4.3 Mã hóa mật khẩu với BCrypt
* Sử dụng `BCryptPasswordEncoder` của Spring Security.
* BCrypt tự động sinh Salt ngẫu nhiên mỗi lần băm, vô hiệu hóa hoàn toàn kỹ thuật tấn công bằng bảng tra băm sẵn (*Rainbow Table*).
* Thuật toán so khớp mật khẩu sử dụng cơ chế so sánh thời gian cố định (*Constant-time comparison*) chống tấn công phân tích thời gian (*Timing Attack*).

### 4.4 Phân quyền dựa trên vai trò (RBAC) & Spring Security Filter Chain
* Kích hoạt `@EnableMethodSecurity(prePostEnabled = true)` cho phép kiểm soát quyền ở cấp độ phương thức:
  * Public: Đăng ký (`/api/v1/users`), Đăng nhập (`/api/v1/auth/login`), Làm mới token (`/api/v1/auth/refresh`).
  * Người dùng xác thực: `@PreAuthorize("isAuthenticated()")` cho xem/sửa hồ sơ, đổi mật khẩu, đăng xuất.
  * Quản trị viên: `@PreAuthorize("hasRole('ADMIN')")` cho tra cứu danh sách người dùng, đổi trạng thái khóa/mở khóa, xóa mềm.
* Xử lý lỗi bảo mật tập trung:
  * `CustomAuthenticationEntryPoint`: Trả về chuẩn JSON `401 Unauthorized` khi chưa có token hợp lệ.
  * `CustomAccessDeniedHandler`: Trả về chuẩn JSON `403 Forbidden` khi User thường cố truy cập API Admin.

---

## 5. TỐI ƯU HIỆU NĂNG & BỘ ĐỆM VỚI REDIS 7

### 5.1 Cache-Aside Pattern cho Hồ sơ Người Dùng
* **Khóa bộ đệm:** `user:profile:<userId>`, TTL (Thời gian tồn tại): 10 phút.
* **Đọc dữ liệu (`@Cacheable`):** Khi gọi `GET /api/v1/users/me`, Spring kiểm tra Redis trước:
  * Nếu Cache Hit: Trả về trực tiếp từ RAM trong vòng < 2ms, giảm tải hoàn toàn cho PostgreSQL.
  * Nếu Cache Miss: Truy vấn DB, ghi kết quả vào Redis rồi trả về Client.
* **Làm mới dữ liệu (`@CacheEvict`):** Khi người dùng cập nhật hồ sơ (`PUT /api/v1/users/me`), bản ghi trên Redis lập tức bị xóa để bảo đảm tính nhất quán dữ liệu (*Data Consistency*).

### 5.2 Chống Tấn Công Dò Mật Khẩu (Login Rate Limiting)
* Khóa Redis: `login:attempts:<email>`, TTL: 15 phút.
* Mỗi lần nhập sai mật khẩu: Redis tăng giá trị đếm bằng phương thức nguyên tử `increment()`.
* Khi số lần sai vượt quá **5 lần**: Server từ chối ngay lập tức với mã lỗi **`HTTP 429 Too Many Requests`**.
* Khi đăng nhập thành công: Lập tức xóa khóa đếm trên Redis.

---

## 6. QUẢN TRỊ CƠ SỞ DỮ LIỆU PHÂN TÁN & FLYWAY

### 6.1 Chiến lược Database-per-Service
Mỗi dịch vụ sở hữu một Database độc lập trên PostgreSQL 16 (chạy cổng host `5433`):
* `user-service` kết nối `user_db`.
* `notification-service` kết nối `notification_db`.
* `chat-service` kết nối `chat_db`.
* Các service tuyệt đối không truy cập trực tiếp vào DB của nhau để bảo đảm tính đóng gói.

### 6.2 Quản lý di chuyển CSDL (Flyway Migrations)
Toàn bộ cấu trúc bảng và dữ liệu mẫu được quản lý phiên bản qua các file SQL:
* `V1__init.sql`: Tạo bảng `roles` (seed `ROLE_USER`, `ROLE_ADMIN`), bảng `users` với chỉ mục unique chữ thường `idx_users_email_lower`, bảng liên kết `user_roles`.
* `V2__refresh_tokens.sql`: Tạo bảng lưu trữ chuỗi băm `token_hash` cho Refresh Token.
* `V3__outbox.sql`: Tạo bảng `outbox_events` phục vụ Transactional Outbox.

### 6.3 Chiến lược Xóa Mềm (Soft Delete)
Khi Admin thực hiện xóa tài khoản (`DELETE /api/v1/users/{id}`):
* Hệ thống chuyển trạng thái `status = 'DELETED'` thay vì xóa cứng dòng dữ liệu.
* Bảo toàn toàn vẹn lịch sử giao dịch và dữ liệu kiểm toán (Audit Trail).

---

## 7. GIAO TIẾP THỜI GIAN THỰC VỚI WEBSOCKET STOMP

### 7.1 Kiến trúc WebSocket trong `chat-service`
* Điểm cuối kết nối (Handshake Endpoint): `/ws-chat`, hỗ trợ SockJS fallback.
* Message Broker STOMP:
  * Prefix gửi tin từ Client: `/app` (định tuyến vào `@MessageMapping`).
  * Prefix phân phối tới Client: `/topic` (Broker phân phát tin nhắn theo phòng `/topic/rooms.{roomId}`).

### 7.2 Xác thực WebSocket tại kênh kết nối (`ChannelInterceptor`)
Do giao thức WebSocket không hỗ trợ truyền Header HTTP linh hoạt ở từng tin nhắn, hệ thống triển khai `WebSocketAuthChannelInterceptor`:
* Bắt gói tin `StompCommand.CONNECT` đầu tiên khi bắt tay.
* Bóc tách Bearer JWT từ Header STOMP `Authorization`.
* Giải mã và gán đối tượng `UserPrincipal` vào phiên kết nối của WebSocket, từ chối kết nối nếu Token không hợp lệ.

### 7.3 Inter-Service REST Call với Token Relay
Khi Admin mở phòng chat, `chat-service` cần hiển thị đầy đủ họ tên, SĐT của khách hàng:
* `chat-service` dùng `RestClient` gọi HTTP sang `GET http://user-service:8081/api/v1/users/{id}`.
* Áp dụng **Token Relay**: Chuyển tiếp nguyên vẹn Bearer Token của Admin sang `user-service`.
* Có cơ chế Fallback an toàn nếu `user-service` bận, bảo đảm giao diện chat không bị treo.

---

## 8. KHẢ NĂNG QUAN SÁT (OBSERVABILITY) & DISTRIBUTED TRACING

### 8.1 Truy vết phân tán qua `X-Trace-Id`
* Mọi request gửi lên hệ thống được gán hoặc tự sinh một mã định danh duy nhất `traceId` (UUID).
* Mã này được truyền liên tục qua các chặng:
  $$\text{HTTP Request} \longrightarrow \text{Outbox JSON DB} \longrightarrow \text{Kafka Message} \longrightarrow \text{Notification Consumer} \longrightarrow \text{Log}$$
* Nhờ cấu hình Logging MDC (`Mapped Diagnostic Context`), toàn bộ log in ra trên cả 2 container đều mang cùng một mã `[trace-e2e-xxxx]`, giúp kỹ sư định vị chính xác hành trình của request khi xảy ra lỗi.

### 8.2 Giám sát chuẩn Spring Boot Actuator
Cả 3 microservices đều cung cấp đầy đủ các endpoint giám sát:
* `/actuator/health`: Kiểm tra trạng thái sống còn của ứng dụng, kết nối DB, Redis, Mailpit, dung lượng đĩa.
* `/actuator/metrics`: Cung cấp số liệu hiệu năng (JVM memory, số lượng request, độ trễ).

---

## 9. CHIẾN LƯỢC KIỂM THỬ (TESTING STRATEGY & E2E)

Dự án áp dụng kim tự tháp kiểm thử nghiêm ngặt:

1. **Unit Test (Tầng đơn vị):** Kiểm thử logic nghiệp vụ của `JwtService`, `UserServiceImpl`, `ChatMessageService` sử dụng JUnit 5 và Mockito.
2. **Web Slice Test:** Kiểm thử Controller cô lập (`@WebMvcTest`) để xác minh validation dữ liệu và mã phản hồi HTTP.
3. **Integration Test với Testcontainers:**
   * Sử dụng thư viện Testcontainers để tự động kéo container Docker của Kafka và PostgreSQL thật khi chạy test.
   * Kiểm tra tính đúng đắn của việc phát sự kiện, lắng nghe consumer và gửi email mà không cần cài đặt môi trường ngoài.
4. **Automated E2E Test Suite (`test-e2e.ps1`):**
   * Script kiểm thử tự động 8 bước nghiêm ngặt:
     1. Đăng ký tài khoản mới (`POST /api/v1/users`).
     2. Thăm dò Mailpit API (`/api/v1/messages`) xác nhận email chào mừng đã tới đích qua Kafka.
     3. Đăng nhập lấy cặp Access & Refresh Token (`POST /api/v1/auth/login`).
     4. Truy vấn Profile với Bearer token (`GET /api/v1/users/me`).
     5. Đổi mật khẩu (`PUT /api/v1/users/me/password`).
     6. Đăng nhập lại với mật khẩu mới thành công.
     7. Đăng xuất thu hồi token (`POST /api/v1/auth/logout`).
     8. Xác nhận Refresh Token cũ bị từ chối với `HTTP 401 Unauthorized`.
   * **Kết quả:** Đạt **100% (8/8 PASS)** trên môi trường Docker Compose.

---

## 10. TỔNG KẾT ĐÁNH GIÁ & HƯỚNG MỞ RỘNG

### 10.1 Kết quả đạt được
* Xây dựng thành công hệ thống Microservices chuẩn sản xuất với khả năng mở rộng cao, không bị thắt nút cổ chai.
* Áp dụng trọn vẹn các mẫu thiết kế kinh điển: **Transactional Outbox**, **Idempotent Consumer**, **Cache-Aside**, **Token Rotation**.
* Tự động hóa hoàn toàn quy trình đóng gói qua Docker Compose chỉ với một câu lệnh khởi chạy duy nhất: `docker compose up -d --build`.

### 10.2 Hướng phát triển tiếp theo (Next Steps)
1. **API Gateway tập trung:** Bổ sung `gateway-service` dùng Spring Cloud Gateway (Port 8080) để hợp nhất đầu mối API và xác thực tập trung.
2. **Quản trị phân tán (Distributed Tracing UI):** Tích hợp OpenTelemetry và Jaeger / Grafana Tempo để hiển thị đồ thị thác nước cho độ trễ request.
3. **Triển khai Kubernetes (K8s):** Chuyển đổi file Docker Compose sang Kubernetes Manifests (Deployments, Services, ConfigMaps, HPA tự động co giãn theo tải).
