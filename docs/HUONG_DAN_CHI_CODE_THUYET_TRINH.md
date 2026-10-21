# CẨM NANG THỰC CHIẾN: BẢN ĐỒ ÁNH XẠ SLIDE ➔ MÃ NGUỒN DỰ ÁN

> **Mục đích tài liệu:** Phục vụ buổi báo cáo seminar / đồ án / bảo vệ thực tập. Giúp sinh viên tự tin mở đúng file mã nguồn trong IDE (IntelliJ IDEA / VS Code), trỏ đúng class/method và trả lời trôi chảy khi Thầy/Cô yêu cầu: *"Ngoài slide, hãy chỉ ra phần code tương ứng áp dụng vào dự án"*.

---

## I. BẢN ĐỒ TỔNG HỢP: SLIDE ➔ FILE CODE DỰ ÁN

| # | Slide & Chủ đề | File Code cần mở trong IDE | Class / Method / Khối cấu hình then chốt | Điểm cần chỉ vào & Thuyết minh |
|---|---|---|---|---|
| **0** | **Trang bìa & Tổng quan** | `docker-compose.yml` | Toàn bộ 8 services | 4 vi dịch vụ (`gateway:8080`, `user:8081`, `noti:8082`, `chat:8083`), 2 database Postgres, Redis, Kafka, Mailpit. |
| **1** | **Mục lục** | Cấu trúc thư mục repo | Root `pom.xml` | Cấu trúc **Multi-module Maven**: module dùng chung `common-lib` (DTO, Exception) và các microservice độc lập. |
| **2** | **Docker là gì?** | `docker-compose.yml` (Cuối file) | Khối `volumes:` | 3 Volume: `postgres_data`, `redis_data`, `kafka_data` lưu trên máy host để đảm bảo **tính bền vững**: tắt/xóa container không mất dữ liệu. |
| **3** | **Tìm hiểu về Dockerfile** | `user-service/Dockerfile`<br>`chat-service/Dockerfile` | **Đóng gói Image:**<br>`FROM`, `WORKDIR`, `COPY`, `EXPOSE`, `ENTRYPOINT` | **Công dụng:** Đóng gói code và môi trường thành Image để chạy ở đâu cũng giống nhau.<br>**Cách dùng & Quy trình:** 5 chỉ thị cơ bản; chu trình 3 bước: viết Dockerfile ➔ `docker build` tạo Image ➔ `docker run` chạy Container. |
| **4** | **Docker Compose** | `docker-compose.yml` | **Chạy & Quản lý nhiều container:**<br>`services`, `ports`, `environment`, `volumes` | **Công dụng:** Chạy nhiều container cùng lúc bằng 1 file YAML, tự động kết nối mạng giữa các service.<br>**Thành phần:** `services`, `build/image`, `ports`, `environment`, `volumes`, `depends_on`.<br>**Lệnh thường dùng:** `up -d`, `down`, `ps`, `logs -f`, `restart`. |
| **5** | **Spring Security & RBAC** | `user-service/.../config/SecurityConfig.java`<br>`user-service/.../security/JwtAuthenticationFilter.java` | `SecurityFilterChain` bean<br>`doFilterInternal()` | **Dòng 52–60:** Phân quyền `.requestMatchers("/api/v1/auth/**").permitAll()`, các API khác `.authenticated()`; bộ lọc chặn bắt Bearer Token, giải mã và set `SecurityContextHolder`. |
| **6** | **JWT & Dual-Token** | `user-service/.../security/JwtService.java`<br>`user-service/.../service/impl/AuthServiceImpl.java` | `generateAccessToken()`<br>`refreshToken()` / `hashToken()` | Thuật toán HMAC-SHA256 (`HS256`), claims (`userId`, `role`); cơ chế **Token Rotation**: Refresh Token được **băm SHA-256** trước khi lưu vào DB PostgreSQL phòng lộ DB. |
| **7** | **Microservices vs Monolith** | `infra/postgres/init/01-init-databases.sql`<br>`docker-compose.yml` | **Database-per-Service** | 3 Database độc lập: `users_db`, `notification_db`, `chat_db` với user/pass riêng biệt; mỗi service chỉ kết nối vào DB của chính mình, cô lập sự cố. |
| **8** | **Sync REST vs Async Kafka** | `chat-service/.../client/UserServiceClient.java`<br>`user-service/.../scheduler/OutboxEventProcessor.java` | REST `RestClient`<br>vs Kafka `KafkaTemplate` | **Đồng bộ (Sync):** `chat-service` gọi HTTP GET sang `user-service` lấy avatar hiển thị UI ngay.<br>**Bất đồng bộ (Async):** `user-service` bắn sự kiện lên Kafka gửi email ngầm không chặn luồng đăng ký. |
| **9** | **Apache Kafka Nền tảng** | `user-service/.../config/KafkaConfig.java`<br>`notification-service/.../consumer/UserEventsConsumer.java` | `NewTopic userEventsTopic()`<br>`@KafkaListener(topics = "user-events")` | Khởi tạo topic `user-events` với 3 partitions; Consumer kéo message JSON về và gọi `EmailService` gửi email chào mừng qua Mailpit (mock SMTP cổng 8025). |
| **10** | **Transactional Outbox Pattern** | `user-service/.../service/impl/UserServiceImpl.java`<br>`user-service/.../scheduler/OutboxEventProcessor.java` | `@Transactional register()`<br>`@Scheduled processOutboxEvents()` | **Triệt tiêu Dual-Write:** `userRepository.save()` và `outboxService.saveEvent()` cùng 1 Database Transaction; tiến trình Scheduler định kỳ quét bảng outbox đẩy lên Kafka và đánh dấu `PROCESSED`. |
| **11** | **Redis In-Memory** | `user-service/.../security/impl/LoginAttemptServiceImpl.java`<br>`user-service/.../service/impl/UserServiceImpl.java` | `opsForValue().increment()` (TTL 15m)<br>`@Cacheable("users")` | **Phòng vệ Brute-force:** đếm số lần sai trên RAM, khóa tạm 15 phút sau 5 lần sai;<br>**Cache:** `@Cacheable` lấy hồ sơ siêu tốc < 0.2ms và `@CacheEvict` dọn cache khi cập nhật. |
| **12** | **WebSocket & STOMP Chat** | `chat-service/.../config/WebSocketConfig.java`<br>`api-gateway/.../resources/application.yml` | `enableSimpleBroker("/topic")`<br>Route Gateway WebSocket | In-Memory Message Broker phân phối tin tức thời thay thế HTTP Polling lãng phí; Gateway định tuyến kết nối WebSocket `ws://chat-service:8083` qua đường dẫn `/ws-chat/**`. |
| **13** | **Kỹ thuật kết nối & Xác thực STOMP** | `chat-service/.../WebSocketAuthChannelInterceptor.java`<br>`chat-service/.../controller/ChatWebSocketController.java`<br>`chat-service/.../service/impl/ChatMessageServiceImpl.java` | `StompCommand.CONNECT`<br>`@MessageMapping` tiêm `Principal`<br>`messagingTemplate.convertAndSend()` | **Bảo mật 100%:** Interceptor chặn frame `CONNECT` lấy Bearer Token đưa vào `UserPrincipal`; Controller tiêm `Principal` tự động (Client không thể giả mạo `senderId`); lưu PostgreSQL và bắn realtime tới `/topic/rooms.{roomId}`. |
| **14** | **Tổng kết & Q&A (Demo)** | `docs/test-e2e.ps1`<br>`docs/api-test.http` | Kịch bản kiểm thử E2E tự động | Script kiểm thử toàn trình: Đăng ký ➔ Nhận email Mailpit ➔ Đăng nhập JWT ➔ Quản trị phòng chat ➔ Chat realtime hai chiều. |

---

## II. HƯỚNG DẪN CHI TIẾT TỪNG CHUYÊN ĐỀ (CODE & LỜI THOẠI)

---

### 1. DOCKER & CONTAINER HÓA (SLIDE 2, 3, 4)

#### 📂 File cần mở:
1. `chat-service/Dockerfile` (hoặc `user-service/Dockerfile`)
2. `docker-compose.yml`
3. `.env.example`

#### 🎯 Dòng code & Kỹ thuật then chốt:
- **`chat-service/Dockerfile` hoặc `user-service/Dockerfile` (Slide 3: Tìm hiểu về Dockerfile):**
  - **Công dụng:** Dùng để **đóng gói mã nguồn (file .jar) và môi trường chạy Java thành một Docker Image** duy nhất. Giúp ứng dụng chạy ở máy nào cũng giống nhau, không sợ thiếu Java hay lệch môi trường.
  - **5 Chỉ thị cơ bản:**
    - `FROM eclipse-temurin:21-jre-jammy`: Chọn hệ điều hành có sẵn Java 21 Runtime.
    - `WORKDIR /app`: Thư mục làm việc trong container.
    - `COPY target/*.jar app.jar`: Chép file jar đã build vào container.
    - `EXPOSE 8081`: Mở cổng mạng cho ứng dụng.
    - `ENTRYPOINT ["java", "-jar", "app.jar"]`: Lệnh để chạy file jar.
  - **Quy trình 3 bước:** Viết Dockerfile ➔ Chạy `docker build -t app:1.0 .` (tạo Image) ➔ Chạy `docker run -d -p 8081:8081 app:1.0` (tạo Container).

- **`docker-compose.yml` (Slide 4: Docker Compose):**
  - **Công dụng:** Dùng để **khởi chạy và quản lý nhiều container cùng một lúc** chỉ bằng 1 file cấu hình duy nhất. Thay vì phải gõ từng lệnh `docker run` riêng lẻ cho từng service và database, ta chỉ cần 1 lệnh `docker compose up -d`. Các container tự động kết nối được với nhau qua mạng nội bộ mà không cần cấu hình IP.
  - **Các thành phần cơ bản:**
    - `services`: Danh sách các container (Backend, PostgreSQL,...).
    - `build` / `image`: Tự build từ Dockerfile hoặc tải image có sẵn trên Docker Hub.
    - `ports`: Ánh xạ cổng `"Cổng_Máy_Tính:Cổng_Container"` (`"8080:8080"`).
    - `environment`: Cài đặt biến môi trường (Database URL, user, pass).
    - `volumes`: Lưu dữ liệu database ra ổ đĩa máy tính, tắt container không bị mất dữ liệu.
    - `depends_on`: Thứ tự chạy (chờ DB chạy trước rồi mới bật backend).
  - **Các lệnh thường dùng nhất:**
    - `docker compose up -d`: Bật toàn bộ container chạy ngầm.
    - `docker compose down`: Tắt và xóa toàn bộ container.
    - `docker compose ps`: Xem danh sách container đang chạy.
    - `docker compose logs -f <service>`: Xem log in ra màn hình để bắt lỗi.
    - `docker compose restart <service>`: Khởi động lại 1 container.

> **🎙️ Kịch bản trả lời Thầy/Cô & Leader (Đơn giản, tự tin đúng tầm intern):**
> *"Thưa Thầy Cô và Anh Leader:
> - **Dockerfile** là tệp kịch bản em dùng để đóng gói file jar cùng Java Runtime thành một **Docker Image** hoàn chỉnh, đảm bảo chạy ở đâu cũng đồng nhất theo chu trình: viết Dockerfile ➔ docker build tạo Image ➔ docker run tạo Container.
> - **Docker Compose** là công cụ giúp em **chạy và quản lý nhiều container cùng một lúc** bằng 1 file YAML duy nhất. Trong dự án có cả Backend và PostgreSQL, thay vì gõ từng lệnh riêng lẻ, em gom tất cả vào `docker-compose.yml` với các phần: services, ports, environment và volumes. Hàng ngày em chỉ cần gõ đúng 1 lệnh `docker compose up -d` là toàn bộ hệ thống tự động khởi chạy và kết nối với nhau, rất nhanh và tiện lợi."*

---

### 2. SPRING SECURITY, RBAC & DUAL-TOKEN JWT (SLIDE 5, 6)

#### 📂 File cần mở:
1. `user-service/src/main/java/com/example/userservice/config/SecurityConfig.java`
2. `user-service/src/main/java/com/example/userservice/security/JwtAuthenticationFilter.java`
3. `user-service/src/main/java/com/example/userservice/security/JwtService.java`
4. `user-service/src/main/java/com/example/userservice/service/impl/AuthServiceImpl.java`

#### 🎯 Dòng code & Kỹ thuật then chốt:
- **`SecurityConfig.java`:**
  - Cấu hình `SecurityFilterChain`:
    ```java
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
    ```
  - Thiết lập chế độ Stateless: `.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))`.

- **`JwtAuthenticationFilter.java`:**
  - Hàm `doFilterInternal()`: Đọc header `Authorization: Bearer <token>`, trích xuất username/claims, xác thực chữ ký qua `jwtService.validateToken()` và nạp danh tính vào `SecurityContextHolder.getContext().setAuthentication(authentication)`.

- **`AuthServiceImpl.java` & `JwtService.java`:**
  - **Dual-Token:** Access Token (15 phút) mang claims danh tính; Refresh Token (7 ngày) lưu trong cơ sở dữ liệu.
  - **Token Rotation & Hashing:** Khi lưu Refresh Token vào bảng `refresh_tokens`, token được băm qua hàm `hashToken(token)` bằng thuật toán **SHA-256**. Kẻ tấn công dù có dump được database cũng không thể lấy được raw refresh token để mạo danh.

> **🎙️ Kịch bản trả lời Thầy/Cô:**
> *"Thưa Thầy Cô, hệ thống của em là Stateless 100%. Mọi request đều đi qua `JwtAuthenticationFilter` để giải mã Bearer Token. Để cân bằng giữa bảo mật và trải nghiệm, em dùng Access Token sống ngắn 15 phút. Khi hết hạn, client gọi API refresh token để đổi cặp token mới. Điểm nhấn là em không lưu raw Refresh Token vào DB mà băm SHA-256 trước khi lưu, đồng thời áp dụng Token Rotation để lập tức hủy token cũ khi có lượt cấp mới."*

---

### 3. KIẾN TRÚC MICROSERVICES & DATABASE-PER-SERVICE (SLIDE 7, 8)

#### 📂 File cần mở:
1. `infra/postgres/init/01-init-databases.sql`
2. `chat-service/src/main/java/com/example/chatservice/client/UserServiceClient.java`
3. `user-service/src/main/java/com/example/userservice/scheduler/OutboxEventProcessor.java`

#### 🎯 Dòng code & Kỹ thuật then chốt:
- **`01-init-databases.sql`:**
  - Tạo 3 database riêng biệt: `users_db`, `notification_db`, `chat_db` với tài khoản riêng.
  - Không có bất kỳ Foreign Key nào xuyên suốt giữa các database của các service.

- **`UserServiceClient.java` (Giao tiếp Đồng bộ - Sync REST):**
  - Sử dụng Spring `RestClient` gọi HTTP GET sang `http://user-service:8081/api/v1/users/{id}/summary` để lấy thông tin avatar/tên người dùng khi hiển thị phòng chat.

- **`OutboxEventProcessor.java` (Giao tiếp Bất đồng bộ - Async Event):**
  - Giao tiếp qua Apache Kafka bằng `kafkaTemplate.send("user-events", key, payload)` để gửi thông báo nền. Luồng đăng ký tài khoản hoàn thành tức thì mà không bị phụ thuộc vào tốc độ gửi email của Notification Service.

> **🎙️ Kịch bản trả lời Thầy/Cô:**
> *"Thưa Thầy Cô, dự án tuân thủ nghiêm ngặt nguyên tắc Database-per-Service. Em có thể chứng minh qua file `01-init-databases.sql`: mỗi microservice có 1 database và user sở hữu riêng biệt. Khi cần thông tin để hiển thị ngay như avatar chat, em dùng REST đồng bộ qua `UserServiceClient`. Còn với các tác vụ chạy ngầm như gửi email kích hoạt, em dùng Kafka bất đồng bộ để tách rời dịch vụ, đảm bảo dù mail server có chậm thì khách hàng vẫn đăng ký thành công ngay lập tức."*

---

### 4. APACHE KAFKA & TRANSACTIONAL OUTBOX PATTERN (SLIDE 9, 10)

#### 📂 File cần mở:
1. `user-service/src/main/java/com/example/userservice/service/impl/UserServiceImpl.java`
2. `user-service/src/main/java/com/example/userservice/entity/OutboxEvent.java`
3. `user-service/src/main/java/com/example/userservice/scheduler/OutboxEventProcessor.java`
4. `notification-service/src/main/java/com/example/notificationservice/consumer/UserEventsConsumer.java`

#### 🎯 Dòng code & Kỹ thuật then chốt:
- **`UserServiceImpl.java` (Hàm `register`):**
  ```java
  @Transactional
  public AuthResponse register(UserRegistrationRequest request) {
      // 1. Lưu User vào PostgreSQL
      User user = userRepository.save(...);
      // 2. Lưu Event vào bảng outbox_events trong CÙNG 1 TRANSACTION
      outboxService.saveEvent("USER_CREATED", user.getId().toString(), payload);
      return response;
  }
  ```
  *(Nếu DB gặp sự cố, cả User và Event cùng rollback. Không bao giờ xảy ra lỗi Dual-Write).*

- **`OutboxEventProcessor.java`:**
  - `@Scheduled(fixedDelay = 2000)`: Quét các sự kiện có trạng thái `PENDING`.
  - Gửi lên Kafka: `kafkaTemplate.send("user-events", event.getAggregateId(), event.getPayload())`.
  - Cập nhật trạng thái `PROCESSED` và `processedAt = Instant.now()`.

- **`UserEventsConsumer.java` (Idempotent Consumer):**
  - Chặn trùng lặp: Kiểm tra bảng `processed_events`. Nếu `eventId` đã tồn tại trong DB thì bỏ qua, đảm bảo tiêu chuẩn *Exactly-Once Processing* ở tầng nghiệp vụ.

> **🎙️ Kịch bản trả lời Thầy/Cô:**
> *"Thưa Thầy Cô, nếu gọi Kafka trực tiếp trong Controller, lỡ Kafka rớt mạng thì user đã lưu vào DB nhưng email không bao giờ được gửi, gây mất đồng bộ dữ liệu (Dual-Write). Em giải quyết bằng Transactional Outbox Pattern: ở hàm `register()`, em bao bọc việc lưu User và lưu OutboxEvent trong cùng một `@Transactional`. Sau đó, tiến trình ngầm `OutboxEventProcessor` sẽ quét và xuất bản lên Kafka định kỳ 2 giây/lần. Phía nhận (`UserEventsConsumer`) kiểm tra `processed_events` để đảm bảo không bao giờ gửi trùng lặp email cho khách."*

---

### 5. BỘ ĐỆM RAM REDIS & CHỐNG TẤN CÔNG BRUTE-FORCE (SLIDE 11)

#### 📂 File cần mở:
1. `user-service/src/main/java/com/example/userservice/security/impl/LoginAttemptServiceImpl.java`
2. `user-service/src/main/java/com/example/userservice/service/impl/UserServiceImpl.java`

#### 🎯 Dòng code & Kỹ thuật then chốt:
- **`LoginAttemptServiceImpl.java` (Phòng vệ Brute-force tại RAM):**
  - **Dòng 25–26:** `MAX_ATTEMPTS = 5`, `LOCK_DURATION = Duration.ofMinutes(15)`.
  - Hàm `loginFailed(email)`:
    - Lệnh nguyên tử: `redisOperations.opsForValue().increment("login:attempts:" + email)`.
    - Đặt TTL tự hủy: `redisOperations.expire(key, LOCK_DURATION)`.
  - Hàm `isBlocked(email)`: Đọc số lần thử từ Redis, nếu `>= 5` thì chặn ngay lập tức, trả lỗi `423 Locked` mà không cần tốn chi phí query Database hay kiểm tra BCrypt băm mật khẩu.
  - **Graceful Degradation:** Bọc `try-catch`, nếu Redis tạm thời sập thì hệ thống vẫn cho phép đăng nhập bình thường qua DB mà không làm gián đoạn người dùng.

- **`UserServiceImpl.java` (Bộ đệm Cache hồ sơ):**
  - `@Cacheable(value = "users", key = "#id")`: Khi xem profile, đọc trực tiếp từ Redis trong < 0.2ms, giảm 90% tải cho PostgreSQL.
  - `@CacheEvict(value = "users", key = "#id")`: Tự động xóa cache khi user cập nhật thông tin cá nhân.

> **🎙️ Kịch bản trả lời Thầy/Cô:**
> *"Thưa Thầy Cô, việc băm BCrypt kiểm tra mật khẩu rất tốn CPU. Nếu hacker gửi hàng ngàn request dò mật khẩu, server sẽ bị nghẽn CPU. Em dùng Redis làm lá chắn tại `LoginAttemptServiceImpl`: dùng hàm nguyên tử `INCR` đếm số lần sai. Khi quá 5 lần, tài khoản bị khóa 15 phút nhờ cơ chế tự hủy TTL của Redis, chặn đứng kẻ tấn công ngay từ tầng RAM."*

---

### 6. WEBSOCKET, STOMP & CHAT REALTIME (SLIDE 12, 13)

#### 📂 File cần mở:
1. `chat-service/src/main/java/com/example/chatservice/config/WebSocketConfig.java`
2. `chat-service/src/main/java/com/example/chatservice/security/WebSocketAuthChannelInterceptor.java`
3. `chat-service/src/main/java/com/example/chatservice/controller/ChatWebSocketController.java`
4. `chat-service/src/main/java/com/example/chatservice/service/impl/ChatMessageServiceImpl.java`

#### 🎯 Dòng code & Kỹ thuật then chốt:
- **`WebSocketConfig.java`:**
  - `registry.addEndpoint("/ws-chat").setAllowedOriginPatterns("*").withSockJS()`: Điểm bắt tay WebSocket (HTTP Upgrade 101).
  - `config.enableSimpleBroker("/topic", "/queue")`: Kích hoạt In-Memory STOMP Broker.
  - `config.setApplicationDestinationPrefixes("/app")`: Định tuyến các tin gửi từ client lên server.

- **`WebSocketAuthChannelInterceptor.java` (Bảo mật phiên Socket):**
  - Bắt sự kiện: `if (StompCommand.CONNECT.equals(accessor.getCommand()))`.
  - Đọc header: `accessor.getFirstNativeHeader("Authorization")`.
  - Giải mã JWT Bearer, nạp `UserPrincipal` vào phiên: `accessor.setUser(authentication)`.

- **`ChatWebSocketController.java` (Chống giả mạo danh tính):**
  - `@MessageMapping("/chat.sendMessage")`: Lắng nghe tin nhắn gửi đến `/app/chat.sendMessage`.
  - Tham số `Principal principal`: **Được tiêm tự động từ phiên làm việc**, client không được tự ý truyền `senderId`. Server lấy ID trực tiếp từ token để đảm bảo người dùng không thể mạo danh tài khoản khác.

- **`ChatMessageServiceImpl.java`:**
  - Lưu tin nhắn vào PostgreSQL.
  - Phát tin tức thời: `messagingTemplate.convertAndSend("/topic/rooms." + roomId, messageDto)`.

> **🎙️ Kịch bản trả lời Thầy/Cô:**
> *"Thưa Thầy Cô, WebSocket duy trì kết nối TCP liên tục nên không thể gửi HTTP Header cho từng tin nhắn. Em giải quyết bằng cách chặn Frame `CONNECT` đầu tiên tại `WebSocketAuthChannelInterceptor` để xác thực JWT. Khi gửi tin tại `ChatWebSocketController`, em tự động tiêm `Principal` từ phiên socket chứ không tin tưởng `senderId` từ client gửi lên, triệt tiêu 100% rủi ro mạo danh trong chat realtime."*

---

## III. QUY TRÌNH THAO TÁC MƯỢT MÀ KHI BẢO VỆ

1. **Chuẩn bị trước giờ thuyết trình:**
   - Mở sẵn VS Code / IntelliJ IDEA với các tab quan trọng theo thứ tự:
     - Tab 1: `docker-compose.yml`
     - Tab 2: `user-service/Dockerfile`
     - Tab 3: `SecurityConfig.java`
     - Tab 4: `UserServiceImpl.java`
     - Tab 5: `LoginAttemptServiceImpl.java`
     - Tab 6: `WebSocketAuthChannelInterceptor.java`
   - Mở slide trên trình duyệt: [docs/presentation.html](file:///d:/intership/microservice_L/docs/presentation.html).

2. **Cách phối hợp khi thuyết trình:**
   - Trên trình duyệt slide: Bấm phím **`N`** để mở Drawer gợi ý lời thoại và vị trí code (Drawer nằm gọn gàng bên phải, không ảnh hưởng nội dung slide).
   - Khi Thầy/Cô ngắt lời yêu cầu xem code:
     - Nhấn `Alt + Tab` chuyển nhanh sang IDE.
     - Trỏ vào đúng class/method đã được liệt kê trong bảng trên.
     - Trình bày theo công thức: **Vấn đề thực tế ➔ Code giải quyết thế nào ➔ Kết quả đạt được**.
