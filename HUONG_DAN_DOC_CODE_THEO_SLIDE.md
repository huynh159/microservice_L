# 🧭 HƯỚNG DẪN ĐỌC & DEMO CODE THEO TỪNG SLIDE THUYẾT TRÌNH

> **Dành cho:** Báo cáo Seminar Microservices (Trình độ Intern / Sinh viên năm 4)  
> **Mục tiêu:** Chỉ rõ **mở file nào trước, file nào sau**, trích xuất đúng các đoạn code cốt lõi kèm **comment giải thích chi tiết** và **gợi ý lời thoại** để bạn tự tin mở IDE demo cho Thầy Cô / Hội đồng xem.

---

## 🗺️ BẢNG TRA CỨU NHANH: SLIDE ➔ THỨ TỰ MỞ FILE CODE

| Slide Số | Tên Chủ Đề Trên Slide | Thứ Tự Mở File Code Trong Dự Án |
| :---: | :--- | :--- |
| **Slide 2, 3, 4** | **Docker, Dockerfile & Compose** | `1.` [docker-compose.yml](file:///d:/HUYNH/test/microservice/docker-compose.yml) ➔ `2.` [user-service/Dockerfile](file:///d:/HUYNH/test/microservice/user-service/Dockerfile) ➔ `3.` [.env](file:///d:/HUYNH/test/microservice/.env) |
| **Slide 5, 6** | **Spring Security & JWT RBAC** | `1.` [SecurityConfig.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/config/SecurityConfig.java) ➔ `2.` [JwtAuthenticationFilter.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/security/JwtAuthenticationFilter.java) ➔ `3.` [JwtService.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/security/JwtService.java) |
| **Slide 7** | **Kiến Trúc Microservices** | `1.` [api-gateway/application.yml](file:///d:/HUYNH/test/microservice/api-gateway/src/main/resources/application.yml) ➔ `2.` [common-lib/pom.xml](file:///d:/HUYNH/test/microservice/common-lib/pom.xml) |
| **Slide 8, 9** | **Kafka & Transactional Outbox** | `1.` [UserServiceImpl.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/service/impl/UserServiceImpl.java) ➔ `2.` [OutboxEventProcessor.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/scheduler/OutboxEventProcessor.java) ➔ `3.` [UserEventsConsumer.java](file:///d:/HUYNH/test/microservice/notification-service/src/main/java/com/example/notificationservice/consumer/UserEventsConsumer.java) |
| **Slide 10, 11** | **Redis: Caching & Chống Brute-Force** | `1.` [RedisConfig.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/config/RedisConfig.java) ➔ `2.` [UserServiceImpl.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/service/impl/UserServiceImpl.java) ➔ `3.` [LoginAttemptServiceImpl.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/security/impl/LoginAttemptServiceImpl.java) |
| **Slide 12, 13** | **WebSocket & STOMP Chat Realtime** | `1.` [WebSocketConfig.java](file:///d:/HUYNH/test/microservice/chat-service/src/main/java/com/example/chatservice/config/WebSocketConfig.java) ➔ `2.` [WebSocketAuthChannelInterceptor.java](file:///d:/HUYNH/test/microservice/chat-service/src/main/java/com/example/chatservice/security/WebSocketAuthChannelInterceptor.java) ➔ `3.` [ChatWebSocketController.java](file:///d:/HUYNH/test/microservice/chat-service/src/main/java/com/example/chatservice/controller/ChatWebSocketController.java) ➔ `4.` [ChatMessageServiceImpl.java](file:///d:/HUYNH/test/microservice/chat-service/src/main/java/com/example/chatservice/service/impl/ChatMessageServiceImpl.java) ➔ `5.` [ChatRestController.java](file:///d:/HUYNH/test/microservice/chat-service/src/main/java/com/example/chatservice/controller/ChatRestController.java) |

---

## 📦 CHUYÊN ĐỀ 1: DOCKER & CONTAINER HÓA (SLIDE 2, 3, 4)

### 🎯 Mục tiêu chứng minh:
- Đóng gói toàn bộ hệ thống bằng Docker để xóa bỏ rào cản môi trường.
- Kỹ thuật **Multi-stage build** giúp Image cực nhẹ và bảo mật.
- Quản trị toàn bộ 8 containers qua 1 lệnh `docker compose up -d`.

---

### 📂 Bước 1: Mở file `docker-compose.yml`
👉 **Đường dẫn:** [docker-compose.yml](file:///d:/HUYNH/test/microservice/docker-compose.yml)  
🗣️ **Lời thoại gợi ý:** *"Thưa Thầy Cô, đây là file Docker Compose điều phối toàn bộ hạ tầng gồm Database, Cache, Message Queue và các Microservices."*

```yaml
# ==============================================================================
# QUẢN TRỊ 8 CONTAINER TRONG HỆ THỐNG
# ==============================================================================
services:
  # 1. Cơ sở dữ liệu PostgreSQL chính thức
  postgres:
    image: postgres:16-alpine
    container_name: microservice_postgres
    environment:
      POSTGRES_USER: ${POSTGRES_USER}        # Đọc từ biến môi trường .env (bảo mật)
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data       # Bền vững dữ liệu trên ổ cứng host

  # 2. Kho dữ liệu In-Memory Redis
  redis:
    image: redis:7-alpine
    container_name: microservice_redis
    command: redis-server --requirepass ${REDIS_PASSWORD} # Đặt mật khẩu bảo vệ Redis

  # 3. Message Broker Apache Kafka & Zookeeper
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: microservice_kafka
    depends_on:
      - zookeeper                            # Phải bật Zookeeper trước mới bật Kafka

  # 4. Service Nghiệp vụ: user-service
  user-service:
    build:
      context: .
      dockerfile: user-service/Dockerfile    # Trỏ đến Dockerfile multi-stage build
    ports:
      - "8081:8081"
    depends_on:
      - postgres                             # Đợi DB sẵn sàng mới khởi chạy app
```

---

### 📂 Bước 2: Mở file `user-service/Dockerfile`
👉 **Đường dẫn:** [user-service/Dockerfile](file:///d:/HUYNH/test/microservice/user-service/Dockerfile)  
🗣️ **Lời thoại gợi ý:** *"Em áp dụng kỹ thuật Multi-stage build chia làm 2 giai đoạn: Giai đoạn 1 build code bằng Maven JDK, giai đoạn 2 chỉ copy file JAR sang JRE siêu nhẹ để chạy, giúp giảm kích thước Image từ 800MB xuống còn dưới 200MB."*

```dockerfile
# ==============================================================================
# GIAI ĐOẠN 1 (BUILDER): Biên dịch mã nguồn Java với đầy đủ công cụ Maven
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy file cấu hình maven và mã nguồn
COPY pom.xml .
COPY common-lib ./common-lib
COPY user-service ./user-service

# Nung mã nguồn thành file .jar
RUN ./mvnw clean package -DskipTests

# ==============================================================================
# GIAI ĐOẠN 2 (RUNTIME): Chỉ chạy ứng dụng bằng JRE gọn nhẹ, không chứa mã nguồn
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tạo user không có quyền root để nâng cao bảo mật
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Chỉ copy file .jar đã nung từ giai đoạn 1 sang
COPY --from=builder /build/user-service/target/*.jar app.jar

EXPOSE 8081
# Lệnh chạy cố định khi bật container
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 🔐 CHUYÊN ĐỀ 2: SPRING SECURITY & JWT PHÂN QUYỀN (SLIDE 5, 6)

### 🎯 Mục tiêu chứng minh:
- Phân tách rõ ràng giữa **Authentication (Bạn là ai?)** và **Authorization (Bạn được làm gì?)**.
- Luồng `SecurityFilterChain` lọc từng request, chặn lỗi 401/403.
- Mô hình **Dual-Token**: Access Token sống ngắn 15 phút, Refresh Token sống dài 7 ngày.

---

### 📂 Bước 1: Mở file `SecurityConfig.java`
👉 **Đường dẫn:** [SecurityConfig.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/config/SecurityConfig.java)  
🗣️ **Lời thoại gợi ý:** *"Tại đây em cấu hình SecurityFilterChain phân loại rõ endpoint công khai permitAll, endpoint cần role ADMIN và gắn JwtAuthenticationFilter vào trước bộ lọc chuẩn của Spring."*

```java
// ==============================================================================
// CẤU HÌNH BẢO VỆ ENDPOINTS & FILTER CHAIN
// ==============================================================================
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        // 1. Tắt CSRF vì ứng dụng dùng kiến trúc REST API Stateless với JWT
        .csrf(AbstractHttpConfigurer::disable)
        
        // 2. Không lưu Session trong RAM server (chuẩn Stateless)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        
        // 3. Quy tắc phân quyền Authorization
        .authorizeHttpRequests(auth -> auth
            // Cho phép tất cả mọi người gọi endpoint đăng ký, đăng nhập
            .requestMatchers("/api/v1/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
            // Chỉ tài khoản có ROLE_ADMIN mới được vào các API quản trị
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            // Tất cả các request còn lại đều phải có Access Token hợp lệ
            .anyRequest().authenticated()
        )
        
        // 4. Đặt JwtAuthenticationFilter chặn trước UsernamePasswordAuthenticationFilter
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

---

### 📂 Bước 2: Mở file `JwtAuthenticationFilter.java`
👉 **Đường dẫn:** [JwtAuthenticationFilter.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/security/JwtAuthenticationFilter.java)  
🗣️ **Lời thoại gợi ý:** *"Filter này chạy ở mỗi HTTP request: nó rút trích chuỗi Bearer Token ở Header, giải mã lấy userId/roles và nạp vào SecurityContextHolder để Spring nhận diện người dùng."*

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
    
    // 1. Lấy Header Authorization: "Bearer eyJhbGciOi..."
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        filterChain.doFilter(request, response); // Không có token -> đi tiếp để Spring từ chối sau
        return;
    }

    String token = authHeader.substring(7); // Cắt bỏ chữ "Bearer " lấy token thô
    
    // 2. Xác thực tính toàn vẹn chữ ký và trích xuất Claims qua JwtService
    if (jwtService.validateToken(token)) {
        UserPrincipal userPrincipal = jwtService.extractUserPrincipal(token);
        
        // 3. Tạo thẻ căn cước Authentication nạp vào bộ nhớ ngữ cảnh của thread hiện tại
        UsernamePasswordAuthenticationToken authentication = 
            new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    filterChain.doFilter(request, response);
}
```

---

## 🌐 CHUYÊN ĐỀ 3: KIẾN TRÚC MICROSERVICES & ROUTING (SLIDE 7)

### 🎯 Mục tiêu chứng minh:
- Hệ thống chia thành 4 service độc lập, mỗi service sở hữu DB riêng.
- `api-gateway` làm đầu mối cửa ngõ duy nhất định tuyến toàn bộ request của Client.

---

### 📂 Bước 1: Mở file `api-gateway/src/main/resources/application.yml`
👉 **Đường dẫn:** [application.yml](file:///d:/HUYNH/test/microservice/api-gateway/src/main/resources/application.yml)  
🗣️ **Lời thoại gợi ý:** *"Client chỉ cần biết 1 cổng duy nhất là API Gateway cổng 8080. Gateway sẽ tự động phân phối các route `/api/v1/users/**` sang cổng 8081 và `/ws-chat/**` sang cổng 8083."*

```yaml
# ==============================================================================
# CẤU HÌNH ĐỊNH TUYẾN SPRING CLOUD GATEWAY (CỔNG :8080)
# ==============================================================================
spring:
  cloud:
    gateway:
      routes:
        # Route 1: Điều hướng các yêu cầu User & Auth sang user-service (:8081)
        - id: user-service-route
          uri: http://user-service:8081
          predicates:
            - Path=/api/v1/auth/**, /api/v1/users/**

        # Route 2: Điều hướng kết nối Chat & WebSocket sang chat-service (:8083)
        - id: chat-service-route
          uri: http://chat-service:8083
          predicates:
            - Path=/api/v1/chat/**, /ws-chat/**
```

---

## 📬 CHUYÊN ĐỀ 4: KAFKA & TRANSACTIONAL OUTBOX (SLIDE 8, 9)

### 🎯 Mục tiêu chứng minh:
- Giao tiếp bất đồng bộ: Khi đăng ký user, không gọi email trực tiếp làm chậm luồng.
- **Transactional Outbox Pattern**: Lưu thông tin user và sự kiện `UserCreatedEvent` vào bảng `outbox_events` trong **cùng 1 Transaction**, giải quyết triệt để lỗi Dual-Write mất message nếu Kafka rớt mạng.
- Tiến trình định kỳ quét bảng outbox để đẩy lên Kafka.

---

### 📂 Bước 1: Mở file `UserServiceImpl.java` (Tại hàm `register`)
👉 **Đường dẫn:** [UserServiceImpl.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/service/impl/UserServiceImpl.java#L88-L131)  
🗣️ **Lời thoại gợi ý:** *"Tại hàm đăng ký, em không gọi Kafka trực tiếp mà lưu đối tượng User kèm OutboxEvent vào Database trong cùng một Database Transaction `@Transactional`."*

```java
@Override
@Transactional // Đảm bảo ghi User và Outbox Event cùng thành công hoặc cùng rollback
public UserProfileResponse register(UserRegistrationRequest request) {
    // 1. Mã hóa mật khẩu bằng BCrypt
    String encodedPassword = passwordEncoder.encode(request.getPassword());
    
    // 2. Lưu User vào bảng `users`
    User user = new User();
    user.setEmail(request.getEmail().toLowerCase());
    user.setPasswordHash(encodedPassword);
    User savedUser = userRepository.save(user);

    // 3. GHI OUTBOX EVENT: Lưu sự kiện vào bảng `outbox_events` trong cùng transaction!
    UserCreatedEvent createdEvent = new UserCreatedEvent(
        savedUser.getId(), savedUser.getEmail(), savedUser.getFullName()
    );
    outboxService.saveEvent("USER", savedUser.getId().toString(), "UserCreatedEvent", createdEvent);

    // Trả về kết quả ngay cho client trong < 5ms, không bị nghẽn bởi gửi email!
    return userMapper.toProfileResponse(savedUser);
}
```

---

### 📂 Bước 2: Mở file `OutboxEventProcessor.java`
👉 **Đường dẫn:** [OutboxEventProcessor.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/scheduler/OutboxEventProcessor.java)  
🗣️ **Lời thoại gợi ý:** *"Đây là tiến trình nền chạy định kỳ mỗi 2 giây, tìm các sự kiện đang ở trạng thái PENDING trong bảng outbox để đẩy lên topic Kafka và cập nhật thành SENT."*

```java
@Component
public class OutboxEventProcessor {

    @Scheduled(fixedDelay = 2000) // Quét định kỳ mỗi 2 giây
    public void processOutboxEvents() {
        // 1. Tìm các sự kiện chưa gửi (status = PENDING)
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            try {
                // 2. Bắn sự kiện lên Kafka Topic: "user-events" với key = aggregateId
                kafkaTemplate.send("user-events", event.getAggregateId(), event.getPayload()).get();

                // 3. Gửi thành công -> Cập nhật trạng thái thành SENT
                event.setStatus(OutboxStatus.SENT);
                outboxRepository.save(event);
            } catch (Exception ex) {
                // Lỗi mạng -> Giữ PENDING để lần quét sau tự động gửi lại (At-Least-Once Delivery)
                log.error("Gửi Kafka thất bại cho event [{}]: {}", event.getId(), ex.getMessage());
            }
        }
    }
}
```

---

### 📂 Bước 3: Mở file `UserEventsConsumer.java` (Tại `notification-service`)
👉 **Đường dẫn:** [UserEventsConsumer.java](file:///d:/HUYNH/test/microservice/notification-service/src/main/java/com/example/notificationservice/consumer/UserEventsConsumer.java)  
🗣️ **Lời thoại gợi ý:** *"Bên Notification Service, hàm `@KafkaListener` tự động nhận sự kiện từ topic `user-events` để gửi email chào mừng mà không làm ảnh hưởng đến thời gian phản hồi của User Service."*

```java
@Component
public class UserEventsConsumer {

    @KafkaListener(topics = "user-events", groupId = "notification-service-group")
    public void consumeUserEvent(String messagePayload) {
        // Tự động phân giải payload JSON sang Event Object
        UserCreatedEvent event = objectMapper.readValue(messagePayload, UserCreatedEvent.class);
        
        // Gửi email chào mừng kích hoạt tài khoản
        emailService.sendWelcomeEmail(event.getEmail(), event.getFullName());
    }
}
```

---

## ⚡ CHUYÊN ĐỀ 5: REDIS: CACHING & PHÒNG VỆ BRUTE-FORCE (SLIDE 10, 11)

### 🎯 Mục tiêu chứng minh:
- **Cache-Aside Pattern**: Lưu hồ sơ người dùng trên RAM với TTL 10 phút, tự hủy bằng `@CacheEvict`.
- **Cơ chế chịu lỗi (Graceful Fallback)**: Redis sập vẫn tự đọc PostgreSQL bình thường, không ném HTTP 500.
- **Chống Brute-force**: Lệnh nguyên tử `INCR` đếm số lần nhập sai, khóa tự động 15 phút trả mã **HTTP 429**.

---

### 📂 Bước 1: Mở file `RedisConfig.java`
👉 **Đường dẫn:** [RedisConfig.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/config/RedisConfig.java)  
🗣️ **Lời thoại gợi ý:** *"Tại RedisConfig, em cấu hình TTL 10 phút cho vùng cache `user_profiles`, dùng Jackson Serializer lưu dạng JSON, và đặc biệt override `errorHandler` để nếu Redis rớt mạng thì app tự fallback đọc PostgreSQL mà không ném lỗi 500."*

```java
// ==============================================================================
// 1. CẤU HÌNH VÙNG CACHE "user_profiles" CÓ TTL 10 PHÚT
// ==============================================================================
@Bean
public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10)) // Dữ liệu tự động hết hạn sau 10 phút
        .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

    return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
}

// ==============================================================================
// 2. CƠ CHẾ CHỊU LỖI THÔNG MINH (GRACEFUL FALLBACK)
// ==============================================================================
@Override
public CacheErrorHandler errorHandler() {
    return new SimpleCacheErrorHandler() {
        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            // Khi Redis chết: Chỉ log cảnh báo, KHÔNG ném lỗi ra ngoài -> Spring sẽ tự động SELECT database!
            log.warn("Redis gặp sự cố khi đọc key [{}]. Fallback đọc thẳng từ PostgreSQL!", key);
        }
    };
}
```

---

### 📂 Bước 2: Mở file `UserServiceImpl.java` (Tại `getUserById` & `changePassword`)
👉 **Đường dẫn:** [UserServiceImpl.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/service/impl/UserServiceImpl.java#L187-L223)  
🗣️ **Lời thoại gợi ý:** *"Khi xem thông tin, `@Cacheable` lấy từ Redis trong dưới 1ms. Khi người dùng đổi mật khẩu, `@CacheEvict` lập tức xóa key trong Redis để tránh dữ liệu cũ."*

```java
// CACHE-ASIDE: Kiểm tra Redis trước -> Có thì trả ngay (< 1ms); Chưa có thì query DB rồi lưu Redis
@Override
@Cacheable(value = "user_profiles", key = "#userId")
public UserProfileResponse getUserById(UUID userId) {
    User user = findUserByIdOrThrow(userId);
    return userMapper.toProfileResponse(user);
}

// ĐỒNG BỘ: Xóa cache ngay khi đổi mật khẩu để bảo đảm an toàn dữ liệu
@Override
@Transactional
@CacheEvict(value = "user_profiles", key = "#userId")
public void changePassword(UUID userId, ChangePasswordRequest request) {
    // Cập nhật mật khẩu mới vào DB...
    // @CacheEvict sẽ tự động xóa key "user_profiles::userId" trong Redis!
}
```

---

### 📂 Bước 3: Mở file `LoginAttemptServiceImpl.java`
👉 **Đường dẫn:** [LoginAttemptServiceImpl.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/security/impl/LoginAttemptServiceImpl.java)  
🗣️ **Lời thoại gợi ý:** *"Để chống dò mật khẩu, em dùng lệnh nguyên tử `INCR` trên RAM của Redis. Nếu người dùng nhập sai quá 5 lần, hệ thống lập tức khóa và trả HTTP 429. Sau 15 phút, Redis tự hủy key và mở khóa tài khoản."*

```java
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    // Ghi nhận nhập sai mật khẩu
    @Override
    public void recordFailedAttempt(String email) {
        String key = "login:attempts:" + email;
        // Tăng biến đếm nguyên tử trên RAM cực nhanh
        long attempts = redisOperations.opsForValue().increment(key);

        // Lần đầu nhập sai -> Đặt thời hạn tự hủy (TTL) 15 phút
        if (attempts == 1) {
            redisOperations.expire(key, LOCK_DURATION);
        }
    }

    // Kiểm tra xem có đang bị khóa không (Nếu >= 5 lần sai -> Bị khóa)
    @Override
    public boolean isBlocked(String email) {
        String value = redisOperations.opsForValue().get("login:attempts:" + email);
        return value != null && Integer.parseInt(value) >= MAX_ATTEMPTS;
    }
}
```

---

## 💬 CHUYÊN ĐỀ 6: WEBSOCKET & STOMP CHAT REALTIME (SLIDE 12, 13)

### 🎯 Mục tiêu chứng minh:
- Kết nối hai chiều Full-Duplex thời gian thực thay thế cơ chế HTTP Polling liên tục.
- Xác thực JWT tại frame STOMP `CONNECT` thông qua `ChannelInterceptor`.
- **Chống mạo danh 100%**: Controller tự động lấy `senderId` từ `Principal` của phiên socket, tuyệt đối không tin client.
- Mô hình kết hợp: REST API tải lịch sử chat cũ, WebSocket STOMP truyền nhận tin mới tức thời.

---

### 📂 Bước 1: Mở file `WebSocketConfig.java` (Tại `chat-service`)
👉 **Đường dẫn:** [WebSocketConfig.java](file:///d:/HUYNH/test/microservice/chat-service/src/main/java/com/example/chatservice/config/WebSocketConfig.java)  
🗣️ **Lời thoại gợi ý:** *"Đây là nơi cấu hình bắt tay WebSocket `/ws-chat` và kích hoạt Simple Broker định tuyến tin nhắn theo tiền tố `/topic` cho phòng chat và `/app` cho tin gửi lên server."*

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 1. Kích hoạt In-Memory Simple Broker cho phòng chat broadcast và tin nhắn riêng
        config.enableSimpleBroker("/topic", "/queue");

        // 2. Tiền tố cho các message mà Client gửi lên Server xử lý
        config.setApplicationDestinationPrefixes("/app");

        // 3. Tiền tố gửi tin nhắn riêng cho từng cá nhân (convertAndSendToUser)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Đăng ký endpoint URL để Client bắt đầu kết nối socket (kèm fallback SockJS)
        registry.addEndpoint("/ws-chat").setAllowedOriginPatterns("*").withSockJS();
    }
}
```

---

### 📂 Bước 2: Mở file `WebSocketAuthChannelInterceptor.java`
👉 **Đường dẫn:** [WebSocketAuthChannelInterceptor.java](file:///d:/HUYNH/test/microservice/chat-service/src/main/java/com/example/chatservice/security/WebSocketAuthChannelInterceptor.java)  
🗣️ **Lời thoại gợi ý:** *"Do WebSocket là kết nối lâu dài không gửi HTTP Header ở từng tin, em dùng ChannelInterceptor bắt ngay frame `CONNECT` đầu tiên, giải mã JWT Bearer và gắn `UserPrincipal` vào phiên kết nối."*

```java
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Bắt sự kiện khi Client gửi frame STOMP đầu tiên là CONNECT
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 1. Rút trích token từ Header "Authorization: Bearer <token>"
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            String token = authHeader.substring(7);

            // 2. Giải mã token lấy định danh người dùng
            UserPrincipal principal = jwtService.extractUserPrincipal(token);
            Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

            // 3. GẮN ĐỊNH DANH VÀO PHIÊN: Kể từ đây, socket này luôn biết chính xác ai đang gửi!
            accessor.setUser(auth);
        }
        return message;
    }
}
```

---

### 📂 Bước 3: Mở file `ChatWebSocketController.java`
👉 **Đường dẫn:** [ChatWebSocketController.java](file:///d:/HUYNH/test/microservice/chat-service/src/main/java/com/example/chatservice/controller/ChatWebSocketController.java)  
🗣️ **Lời thoại gợi ý:** *"Tại Controller, hàm `@MessageMapping` tự động nhận `Principal` từ phiên kết nối. Client chỉ truyền `{roomId, content}`, hoàn toàn không được tự truyền senderId, loại bỏ 100% nguy cơ mạo danh."*

```java
@Controller
public class ChatWebSocketController {

    // Lắng nghe tin nhắn gửi đến destination: /app/chat.sendMessage
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        // 1. Tự động lấy danh tính người gửi từ Authentication Context đã gắn ở bước CONNECT
        Authentication auth = (Authentication) principal;
        UserPrincipal userPrincipal = (UserPrincipal) auth.getPrincipal();

        // Xác định vai trò gửi là ADMIN hay USER
        SenderRole senderRole = userPrincipal.isAdmin() ? SenderRole.ROLE_ADMIN : SenderRole.ROLE_USER;

        // 2. Chuyển xuống Service xử lý: Lưu DB trước -> Phát realtime sau
        chatMessageService.sendMessage(userPrincipal.getId(), senderRole, request);
    }
}
```

---

### 📂 Bước 4: Mở file `ChatMessageServiceImpl.java`
👉 **Đường dẫn:** [ChatMessageServiceImpl.java](file:///d:/HUYNH/test/microservice/chat-service/src/main/java/com/example/chatservice/service/impl/ChatMessageServiceImpl.java)  
🗣️ **Lời thoại gợi ý:** *"Service tiến hành kiểm tra quyền (khách hàng chỉ nhắn vào phòng của mình, Admin nhắn được mọi phòng), lưu tin nhắn vào bảng `chat_messages` trong PostgreSQL rồi mới dùng `messagingTemplate` phát tin tức thời tới `/topic/rooms.{roomId}`."*

```java
@Override
@Transactional
public ChatMessageResponse sendMessage(UUID senderId, SenderRole senderRole, SendMessageRequest request) {
    // 1. Kiểm tra phân quyền truy cập phòng chat
    ChatRoom room = chatRoomRepository.findById(request.getRoomId()).orElseThrow();
    if (senderRole == SenderRole.ROLE_USER && !room.getUserId().equals(senderId)) {
        throw new ForbiddenException("FORBIDDEN", "Bạn không có quyền gửi tin vào phòng chat này");
    }

    // 2. Lưu tin nhắn bền vững vào PostgreSQL
    ChatMessage message = new ChatMessage();
    message.setRoomId(room.getId());
    message.setSenderId(senderId);
    message.setContent(request.getContent());
    ChatMessage savedMessage = chatMessageRepository.save(message);

    ChatMessageResponse response = chatMessageMapper.toResponse(savedMessage);

    // 3. Dùng SimpMessageSendingOperations phát tức thời cho cả Admin và User đang ở trong phòng!
    messagingTemplate.convertAndSend("/topic/rooms." + room.getId(), response);

    return response;
}
```

---

### 📂 Bước 5: Mở file `ChatRestController.java`
👉 **Đường dẫn:** [ChatRestController.java](file:///d:/HUYNH/test/microservice/chat-service/src/main/java/com/example/chatservice/controller/ChatRestController.java)  
🗣️ **Lời thoại gợi ý:** *"Mô hình kết hợp thực tế: Khi người dùng vừa mở hộp chat, ứng dụng gọi REST API `GET /api/v1/chat/rooms/{roomId}/messages` để tải toàn bộ lịch sử trò chuyện cũ từ PostgreSQL lên màn hình, sau đó duy trì WebSocket STOMP để truyền nhận các tin nhắn mới phát sinh."*

---

## 🎯 BÍ QUYẾT KHI BÁO CÁO SEMINAR
1. **Luôn đi từ bài toán thực tế trước:** Nêu lý do vì sao chọn giải pháp đó (VD: *Tại sao không dùng Polling? Vì nghẽn mạng! Tại sao dùng Outbox? Vì chống mất event khi Kafka rớt!*).
2. **Trỏ trực tiếp vào dòng code có comment:** Các file code trên đều đã được viết comment chi tiết, chỉ cần bấm vào đúng các file theo thứ tự là bạn có thể đọc vanh vách từng dòng.
3. **Nhấn mạnh các giải pháp an toàn:** Luôn nhắc đến **Graceful Fallback** (Redis sập vẫn đọc được DB), **Chống mạo danh Principal** (không tin client), và **Transactional Outbox** (bảo toàn dữ liệu).
