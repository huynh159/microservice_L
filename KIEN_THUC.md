# 📖 CẨM NANG ÔN TẬP KIẾN THỨC MICROSERVICES & HƯỚNG DẪN CHẠY DỰ ÁN TẠI NHÀ

Tài liệu này tổng hợp toàn bộ các kiến thức lập trình, kiến trúc hệ thống và hướng dẫn chi tiết từng bước để bạn có thể tải mã nguồn từ GitHub về laptop cá nhân và khởi chạy dễ dàng.

---

## MỤC LỤC
1. [Kiến trúc Tổng quan Hệ thống](#1-kiến-trúc-tổng-quan-hệ-thống)
2. [Tầng Service: Interface vs Implementation](#2-tầng-service-interface-vs-implementation)
3. [Quản trị Giao dịch Database: @Transactional & Dirty Checking](#3-quản-trị-giao-dịch-database-transactional--dirty-checking)
4. [Bảo mật Mật khẩu: Tại sao dùng BCrypt và PasswordEncoder?](#4-bảo-mật-mật-khẩu-tại-sao-dùng-bcrypt-và-passwordencoder)
5. [Chiến lược Dữ liệu: Xóa mềm (Soft Delete) vs Xóa cứng (Hard Delete)](#5-chiến-lược-dữ-liệu-xóa-mềm-soft-delete-vs-xóa-cứng-hard-delete)
6. [Tầng REST Controller & Chuẩn HTTP Status Code](#6-tầng-rest-controller--chuẩn-http-status-code)
7. [Xử lý Ngoại lệ Toàn cục (Global Exception Handling) & traceId](#7-xử-lý-ngoại-lệ-toàn-cục-global-exception-handling--traceid)
8. [Phương pháp Kiểm thử Phần mềm: Unit Test vs Web Slice Test](#8-phương-pháp-kiểm-thử-phần-mềm-unit-test-vs-web-slice-test)
9. [Xác thực Stateless với JWT (Access Token vs Refresh Token)](#9-xác-thực-stateless-với-jwt-access-token-vs-refresh-token)
10. [Cơ chế Xoay vòng Refresh Token & Lưu chuỗi băm SHA-256](#10-cơ-chế-xoay-vòng-refresh-token--lưu-chuỗi-băm-sha-256)
11. [Chống Brute-force Đăng nhập với Redis (HTTP 429)](#11-chống-brute-force-đăng-nhập-với-redis-http-429)
12. [Phân quyền RBAC & Thu hồi Token khi Đổi mật khẩu](#12-phân-quyền-rbac--thu-hồi-token-khi-đổi-mật-khẩu)
13. [HƯỚNG DẪN CHẠY DỰ ÁN TRÊN LAPTOP Ở NHÀ](#13-hướng-dẫn-chạy-dự-án-trên-laptop-ở-nhà)
14. [CHỨC NĂNG CHAT REALTIME VỚI WEBSOCKET STOMP & GIAO TIẾP MICROSERVICES](#14-chức-năng-chat-realtime-với-websocket-stomp--giao-tiếp-microservices)
15. [KIẾN TRÚC & TƯ DUY XÂY DỰNG API GATEWAY (SPRING CLOUD GATEWAY REACTIVE)](#15-kiến-trúc--tư-duy-xây-dựng-api-gateway-spring-cloud-gateway-reactive)

---

## 1. Kiến trúc Tổng quan Hệ thống

Dự án được xây dựng theo kiến trúc **Microservices hướng sự kiện (Event-Driven Architecture)** gồm:

```
[ Client / Postman / Frontend ]
               │
          HTTPS / REST
               ▼
       ┌───────────────┐
       │ user-service  │ ────▶ PostgreSQL (user_db :5433)
       │ (Port 8081)   │ ────▶ Redis (Cache & chống Brute-force :6379)
       └───────┬───────┘
               │  (Transactional Outbox)
               ▼
         [ Kafka Topic: user-events ]
               │
               ▼
       ┌──────────────────────┐
       │ notification-service │ ────▶ PostgreSQL (notification_db)
       │ (Port 8082)          │ ────▶ Mailpit (SMTP Mock Email Server :8025)
       └──────────────────────┘
```

* **Module `common-lib`:** Thư viện dùng chung, chứa các DTO phản hồi (`ErrorResponse`, `PageResponse`), DTO sự kiện (`UserCreatedEvent`), và các Ngoại lệ nghiệp vụ (`ResourceNotFoundException`, `ConflictException`, `BadRequestException`).
* **Tính độc lập:** Hai service **không gọi HTTP đồng bộ sang nhau**. `user-service` phát sự kiện vào Kafka, `notification-service` tiêu thụ sự kiện để gửi email chào mừng.

---

## 2. Tầng Service: Interface vs Implementation

### Khái niệm
* **`UserService.java` (Interface):** Là **"Bản hợp đồng"** hoặc **"Cuốn thực đơn"**. Chỉ liệt kê danh sách tên hàm, tham số nhận vào và kiểu trả về (không có ruột code).
* **`UserServiceImpl.java` (Implementation):** Dùng từ khóa `implements UserService`, là **"Người thợ thi công"** viết toàn bộ thuật toán, logic nghiệp vụ và truy vấn DB.

### Tại sao bắt buộc phải tách Interface trong Microservices?
1. **Tính độc lập (Loose Coupling):** `UserController` chỉ cần nói chuyện với Interface `UserService`. Controller không cần biết bên dưới dùng PostgreSQL, MySQL hay Redis.
2. **Dễ viết Unit Test:** Khi test Controller, ta chỉ cần tạo đối tượng giả lập (`@MockBean / @MockitoBean`) của `UserService` mà không cần bật cơ sở dữ liệu thật.
3. **Cơ chế Spring Dynamic Proxy / AOP:** Spring tạo ra một lớp vỏ bọc bên ngoài dựa trên Interface này để tự động chèn logic quản trị giao dịch rollback (`@Transactional`), nạp dữ liệu cache (`@Cacheable`) và kiểm tra phân quyền (`@PreAuthorize`).

---

## 3. Quản trị Giao dịch Database: @Transactional & Dirty Checking

### Tính toàn vẹn ACID & Tự động Rollback
Khi một phương thức được đánh dấu `@Transactional`:
* Nếu xảy ra lỗi `RuntimeException` (như `BadRequestException`, `ConflictException`), Spring sẽ tự động **ROLLBACK (hoàn tác)** toàn bộ các lệnh `INSERT`/`UPDATE` trước đó.
* Không bao giờ để lại dữ liệu rác hay trạng thái dở dang trong cơ sở dữ liệu.

### Tại sao đặt `@Transactional(readOnly = true)` ở cấp Class?
* Mặc định, Hibernate bật cơ chế **Dirty Checking** (theo dõi mọi thay đổi của Entity trong bộ nhớ để tự động `UPDATE` xuống DB khi kết thúc phiên).
* Khi đặt `readOnly = true`, Hibernate sẽ **TẮT Dirty Checking**:
  - Tiết kiệm đáng kể dung lượng bộ nhớ RAM (không phải lưu snapshot của Entity).
  - Tối ưu hóa truy vấn `SELECT` chạy nhanh hơn rõ rệt.
* Với các hàm ghi/sửa/xóa dữ liệu, ta ghi đè bằng `@Transactional` riêng.

---

## 4. Bảo mật Mật khẩu: Tại sao dùng BCrypt và PasswordEncoder?

### Cơ chế Salt ngẫu nhiên của BCrypt
* BCrypt tự động sinh một chuỗi ngẫu nhiên (**Salt**) và ghép vào mật khẩu trước khi băm.
* Kết quả: Cùng một mật khẩu (ví dụ `"Password123@"`), nhưng nếu băm 2 lần sẽ cho ra 2 chuỗi hash hoàn toàn khác nhau.
* **Tác dụng:** Vô hiệu hóa hoàn toàn kỹ thuật tấn công giải mã bằng bảng tra băm sẵn (*Rainbow Table*).

### Tại sao KHÔNG THỂ dùng hàm `.equals()`?
* Vì mỗi lần hash ra một kết quả khác nhau, câu lệnh `hash1.equals(hash2)` sẽ luôn luôn trả về `false`.
* Bắt buộc phải dùng hàm:
  ```java
  passwordEncoder.matches(matKhauTho, hashTrongDatabase);
  ```
  Hàm này sẽ tự động trích xuất Salt từ `hashTrongDatabase`, băm `matKhauTho` với Salt đó và so sánh an toàn theo thời gian cố định (*Constant-time comparison*) để chống tấn công phân tích thời gian (*Timing Attack*).

---

## 5. Chiến lược Dữ liệu: Xóa mềm (Soft Delete) vs Xóa cứng (Hard Delete)

* **Xóa cứng (Hard Delete):** Chạy lệnh SQL `DELETE FROM users WHERE id = ...`.
  - **Nguy hiểm trong Microservices:** Nếu các service khác như `Order Service`, `Payment Service`, `Notification Service` đang lưu trữ `userId` trong lịch sử đơn hàng, xóa cứng sẽ biến các bản ghi đó thành **"mồ côi" (Orphan records)**, không thể đối soát người mua là ai.
* **Xóa mềm (Soft Delete):** Chỉ cập nhật trường `status = UserStatus.DELETED`.
  - Người dùng không thể đăng nhập hoặc thao tác được nữa.
  - Nhưng toàn bộ lịch sử giao dịch, tính toàn vẹn dữ liệu và phục vụ kiểm toán tài chính vẫn được bảo toàn nguyên vẹn.

---

## 6. Tầng REST Controller & Chuẩn HTTP Status Code

Controller là cổng tiếp nhận yêu cầu từ bên ngoài, chỉ nhận request, kích hoạt kiểm tra dữ liệu (`@Valid`), gọi Service và trả về DTO.

### Bảng tra cứu mã phản hồi HTTP:
| HTTP Status Code | Tên chuẩn | Ý nghĩa & Khi nào sử dụng |
| :--- | :--- | :--- |
| **200 OK** | OK | Truy vấn dữ liệu hoặc cập nhật thành công (có trả về body). |
| **201 Created** | Created | Tạo mới tài nguyên thành công (Đăng ký tài khoản). Đi kèm Header `Location: /api/v1/users/{id}`. |
| **204 No Content** | No Content | Thao tác thành công nhưng không cần trả về body (Đổi mật khẩu, Xóa mềm) nhằm tiết kiệm băng thông. |
| **400 Bad Request** | Bad Request | Dữ liệu gửi lên sai định dạng, hoặc vi phạm Bean Validation (`@Valid`). |
| **401 Unauthorized**| Unauthorized | Chưa xác thực (chưa đăng nhập, thiếu token hoặc token hết hạn). |
| **403 Forbidden** | Forbidden | Đã đăng nhập nhưng không đủ quyền hạn (User thường cố gọi API của Admin). |
| **404 Not Found** | Not Found | Không tìm thấy tài nguyên theo ID yêu cầu hoặc gõ nhầm URL. |
| **409 Conflict** | Conflict | Xung đột tài nguyên (ví dụ: email đăng ký đã tồn tại trong hệ thống). |
| **429 Too Many Requests** | Rate Limited | Gửi quá nhiều request / đăng nhập sai quá 5 lần (bảo vệ chống Brute-force). |
| **500 Internal Error** | Server Error | Lỗi máy chủ không mong muốn (hệ thống giấu kín thông tin, chỉ trả về traceId). |

---

## 7. Xử lý Ngoại lệ Toàn cục (Global Exception Handling) & traceId

### Cơ chế Hoạt động của `@RestControllerAdvice`
1. Mọi request đi qua tổng đài `DispatcherServlet` của Spring Boot.
2. Khi code gặp lỗi (ở Repository, Service hay Controller), lỗi sẽ bay ngược lên `DispatcherServlet`.
3. Thay vì làm sập ứng dụng, Spring chuyển lỗi cho [GlobalExceptionHandler.java](file:///d:/HUYNH/test/microservice/user-service/src/main/java/com/example/userservice/exception/GlobalExceptionHandler.java).
4. Spring dùng cơ chế **"Khớp nhãn lỗi cụ thể nhất" (Most Specific Match)**:
   - Nếu lỗi là `ConflictException` ➡️ Kích hoạt `@ExceptionHandler(ConflictException.class)` (Trả về 409).
   - Nếu lỗi là `@Valid` sai ➡️ Kích hoạt `@ExceptionHandler(MethodArgumentNotValidException.class)` (Trả về 400 kèm mảng chi tiết các trường sai).
   - Nếu lỗi hệ thống không lường trước ➡️ Rơi vào tấm lưới bảo hiểm cuối cùng `@ExceptionHandler(Exception.class)` (Trả về 500).

### `traceId` là gì?
* Là chuỗi định danh duy nhất (UUID) sinh ra cho mỗi request.
* **Ẩn dụ:** Giống như **"Mã vận đơn bưu phẩm" (Tracking Code)**.
* **Tác dụng:** Trong hệ thống Microservices có hàng triệu lượt gọi API mỗi phút, khi khách hàng báo lỗi kèm mã `traceId: 7d2b8c91...`, kỹ sư lập trình chỉ cần gõ mã này vào hệ thống Log tập trung là tìm ra chính xác dòng lỗi trong vòng 3 giây!

---

## 8. Phương pháp Kiểm thử Phần mềm: Unit Test vs Web Slice Test

Trong dự án, chúng ta đã áp dụng 2 trường phái kiểm thử hiện đại:

1. **Unit Test thuần túy (với Mockito):**
   - File: `UserServiceTest.java`.
   - `@ExtendWith(MockitoExtension.class)`: **KHÔNG bật Spring Context**, không kết nối DB thật.
   - Toàn bộ Database, PasswordEncoder đều là giả lập (`@Mock`).
   - Tốc độ: Chạy 12 test cases chỉ mất **0.3 giây**.
2. **Web Slice Test (với `@WebMvcTest` & `MockMvc`):**
   - File: `UserControllerTest.java`.
   - `@WebMvcTest(UserController.class)`: Chỉ nạp đúng tầng Web (Spring MVC, Jackson JSON, Controller, Advice bắt lỗi). Tầng Service được thay bằng `@MockitoBean`.
   - Kiểm tra chính xác: Định tuyến URL, mã HTTP Status, kiểm tra Header, Bean Validation.
   - Tốc độ: Chạy 14 test cases chỉ mất **0.8 giây**.

---

## 9. Xác thực Stateless với JWT (Access Token vs Refresh Token)

### 9.1 Khái niệm Stateless Authentication
* **Stateful (Session truyền thống):** Máy chủ lưu session ID trong RAM/DB. Khi scale nhiều instance, cần đồng bộ session (Sticky Session hoặc Redis Session), gây nghẽn cổ chai.
* **Stateless (JWT - JSON Web Token):** Máy chủ **không lưu Access Token**. Mọi thông tin danh tính được đóng gói ngay trong chuỗi Token và ký bằng Secret Key (`HMAC-SHA256`). Mọi instance của microservice đều có thể tự giải mã và xác thực chữ ký mà không cần gọi DB.

### 9.2 Cấu trúc 3 phần của JSON Web Token
Chuỗi JWT gồm: `Header.Payload.Signature`
1. **Header:** `{"alg": "HS256", "typ": "JWT"}`
2. **Payload (Claims):** Chứa dữ liệu định danh theo chuẩn RFC 7519:
   - `sub` (Subject): UUID của người dùng (`userId`).
   - `email`: Địa chỉ email của người dùng.
   - `roles`: Danh sách quyền hạn (`["ROLE_USER", "ROLE_ADMIN"]`).
   - `iat` (Issued At): Thời điểm tạo token.
   - `exp` (Expiration): Thời điểm hết hạn (15 phút).
   - `jti` (JWT ID): Mã UUID duy nhất chống tấn công phát lại (*Replay Attack*).
3. **Signature:** Chữ ký số tạo từ `HMACSHA256(base64(Header) + "." + base64(Payload), SecretKey)`. Nếu kẻ gian sửa đổi bất kỳ ký tự nào trong Payload, chữ ký sẽ không khớp và token bị từ chối ngay lập tức.

### 9.3 Tại sao cần phân tách Cặp Token: Access Token & Refresh Token?
* **Access Token (Hạn ngắn - 15 phút):** Dùng để gọi các API nghiệp vụ (`Authorization: Bearer <token>`). Do có hạn ngắn, nếu lỡ bị lộ thì kẻ gian cũng chỉ lợi dụng được tối đa 15 phút.
* **Refresh Token (Hạn dài - 7 ngày):** Dùng để đổi lấy cặp Access Token + Refresh Token mới mà không bắt người dùng phải đăng nhập lại liên tục.

---

## 10. Cơ chế Xoay vòng Refresh Token & Lưu chuỗi băm SHA-256

### 10.1 Tại sao KHÔNG lưu token thô trong Database? (Nguyên lý Zero-Knowledge)
* **Quy tắc an ninh:** Giống như mật khẩu, Refresh Token thô gửi về Client, còn trong DB chỉ lưu **chuỗi băm SHA-256** (`token_hash`).
* Nếu hacker xâm nhập được cơ sở dữ liệu, chúng **chỉ thấy chuỗi hash một chiều** và không thể dùng nó để gọi API `/auth/refresh`!
* **Tại sao dùng SHA-256 thay vì BCrypt cho Token Hash?**
  - BCrypt có salt ngẫu nhiên nên không thể tra cứu trực tiếp bằng câu lệnh `SELECT ... WHERE token_hash = ?`.
  - SHA-256 là hàm băm tất định (Deterministic - cùng đầu vào luôn ra 64 ký tự hex cố định), cho phép tìm kiếm tức thì `O(1)` qua Database Index mà vẫn đảm bảo tính bảo mật một chiều không thể đảo ngược.

### 10.2 Cơ chế Xoay vòng Token (Refresh Token Rotation)
1. Khi Client gửi Refresh Token cũ lên `/api/v1/auth/refresh`:
   - Server băm SHA-256 token gửi lên và tìm trong bảng `refresh_tokens`.
   - Nếu token **đã bị thu hồi trước đó** (`isRevoked() == true`): **CẢNH BÁO NGUY HIỂM!** Token này có thể đã bị kẻ gian đánh cắp và tái sử dụng. Hệ thống từ chối ngay lập tức (`401 Unauthorized`).
   - Nếu token hợp lệ: Đánh dấu token cũ là ĐÃ THU HỒI (`revoked_at = now()`, `replaced_by = hash_token_mới`).
   - Sinh Refresh Token mới, băm và lưu vào DB.
   - Cấp cặp Access Token mới + Refresh Token mới cho Client.

---

## 11. Chống Brute-force Đăng nhập với Redis (HTTP 429)

### 11.1 Ngăn chặn tấn công vét cạn mật khẩu
* Kẻ gian có thể dùng bot chạy thử hàng ngàn mật khẩu mỗi phút.
* Để ngăn chặn, hệ thống áp dụng cơ chế đếm số lần sai bằng Redis:
  - Khóa: `login:attempts:<email>`
  - TTL (Thời gian hết hạn): 15 phút (900 giây).
  - Khi nhập sai: Tăng bộ đếm (`increment`). Ở lần sai đầu tiên, đặt TTL 15 phút.
  - Khi số lần sai ≥ 5: Từ chối ngay lập tức với mã HTTP `429 Too Many Requests`.
  - Khi đăng nhập thành công: Xóa ngay khóa trên Redis.

### 11.2 Bảo mật chống Dò tìm tài khoản (Zero User Enumeration)
* **Quy tắc vàng:** Dù không tìm thấy email hay nhập sai mật khẩu, hệ thống **luôn trả về một thông báo duy nhất**:
  > `"Email hoặc mật khẩu không chính xác"` (HTTP 401 Unauthorized).
* **Tuyệt đối không phân biệt** *"Email không tồn tại"* hay *"Mật khẩu sai"*, vì điều đó sẽ giúp kẻ tấn công biết email nào đã đăng ký trên hệ thống.

---

## 12. Phân quyền RBAC & Thu hồi Token khi Đổi mật khẩu

### 12.1 Phân quyền dựa trên vai trò (Role-Based Access Control)
* Sử dụng `@EnableMethodSecurity(prePostEnabled = true)` và `@PreAuthorize`:
  - Public: Đăng ký (`POST /api/v1/users`), Đăng nhập (`POST /api/v1/auth/login`), Làm mới token (`POST /api/v1/auth/refresh`), Swagger UI, Actuator Health.
  - Yêu cầu đăng nhập (`@PreAuthorize("isAuthenticated()")`): Xem & sửa profile cá nhân (`/api/v1/users/me`), Đổi mật khẩu (`/api/v1/users/me/password`), Đăng xuất (`/api/v1/auth/logout`).
  - Quản trị viên (`@PreAuthorize("hasRole('ADMIN')")`): Tra cứu danh sách (`GET /api/v1/users`), Xem theo ID, Khóa/mở khóa (`PATCH /status`), Xóa mềm (`DELETE`).
* Bộ xử lý tập trung:
  - Chưa đăng nhập gọi API bảo vệ -> `CustomAuthenticationEntryPoint` trả `HTTP 401 UNAUTHORIZED`.
  - User thường gọi API của Admin -> `CustomAccessDeniedHandler` trả `HTTP 403 FORBIDDEN`.

### 12.2 Thu hồi Token toàn diện khi Đổi mật khẩu
* Khi người dùng đổi mật khẩu thành công (`PUT /api/v1/users/me/password`):
* Hệ thống kích hoạt câu lệnh `refreshTokenRepository.revokeAllByUserId(userId)`:
  - Mọi Refresh Token đang hoạt động của người dùng trên mọi thiết bị/trình duyệt khác lập tức bị thu hồi (`revoked_at = CURRENT_TIMESTAMP`).
  - Buộc tất cả các phiên đăng nhập khác phải kết thúc, bảo vệ an toàn tối đa cho tài khoản.

---

## 13. HƯỚNG DẪN CHẠY DỰ ÁN TRÊN LAPTOP Ở NHÀ

Khi bạn về nhà và mở laptop, hãy làm theo các bước sau:

### Bước 1: Đẩy code từ máy hiện tại lên GitHub (Thực hiện trước khi tắt máy)
Mở terminal tại máy hiện tại:
```powershell
# 1. Thêm link GitHub của bạn (nếu chưa thêm)
git remote add origin https://github.com/<tai-khoan-cua-ban>/<ten-repo>.git

# 2. Đẩy nhánh master lên GitHub
git push -u origin master
```

---

### Bước 2: Kéo code về Laptop cá nhân
Mở terminal trên laptop ở nhà:
```bash
git clone https://github.com/<tai-khoan-cua-ban>/<ten-repo>.git
cd <ten-repo>
```

---

### Bước 3: Khởi động Hạ tầng Docker (PostgreSQL, Redis, Kafka, Mailpit)
Tại thư mục gốc dự án:
```powershell
# Sao chép file môi trường (nếu chưa có)
copy .env.example .env

# Khởi động các container hạ tầng
docker compose -f docker-compose.infra.yml up -d
```
Kiểm tra xem các container đã khỏe mạnh chưa:
```powershell
docker compose -f docker-compose.infra.yml ps
```
* **PostgreSQL:** `localhost:5433` (Database: `user_db` và `notification_db`)
* **Redis:** `localhost:6379`
* **Kafka:** `localhost:9092`
* **Mailpit (Giao diện Web xem email giả lập):** Mở trình duyệt vào `http://localhost:8025`

---

### Bước 4: Chạy Kiểm thử hoặc Khởi động `user-service`

#### Cách 1: Chạy toàn bộ bài kiểm thử (để kiểm tra môi trường)
```powershell
./mvnw.cmd clean test
```
*(Nếu là máy Mac / Linux thì gõ `./mvnw clean test`)*

#### Cách 2: Khởi động trực tiếp `user-service`
```powershell
./mvnw.cmd -pl user-service spring-boot:run
```

Khi ứng dụng khởi động thành công trên cổng `8081`:
* **Swagger UI (Tài liệu API tương tác trực tiếp):** Mở trình duyệt vào link:
  👉 `http://localhost:8081/swagger-ui.html`
* **Kiểm tra sức khỏe hệ thống (Health Check):**
  👉 `http://localhost:8081/actuator/health`

---

### Bước 5: Chạy `user-service` bằng Dockerfile (Đóng gói Container)

Dự án đã có sẵn [Dockerfile](file:///d:/HUYNH/test/microservice/user-service/Dockerfile) chuẩn Multi-stage build với JDK 21:

```powershell
# 1. Đóng gói ứng dụng thành file .jar
./mvnw.cmd -pl user-service clean package -DskipTests

# 2. Xây dựng Docker Image cho user-service
docker build -t user-service:1.0.0 -f user-service/Dockerfile .

# 3. Chạy container user-service kết nối vào mạng infra
docker run -d --name user-service -p 8081:8081 --network microservice-network user-service:1.0.0
```

---

## 14. CHỨC NĂNG CHAT REALTIME VỚI WEBSOCKET STOMP & GIAO TIẾP MICROSERVICES

### 14.1 Bản chất WebSocket vs HTTP Polling
- **HTTP (Request-Response):** Muốn kiểm tra tin nhắn mới, client phải liên tục gửi request thăm dò (Polling). Tốn băng thông và tạo độ trễ lớn.
- **WebSocket:** Nâng cấp từ kết nối HTTP thông qua cú bắt tay (Handshake: `Upgrade: websocket`). Tạo ra đường ống TCP 2 chiều (Full-Duplex) giữ mở liên tục. Server có thể chủ động đẩy (Push) dữ liệu xuống client ngay lập tức với độ trễ tính bằng mili-giây.

### 14.2 Giao thức STOMP (Simple Text Oriented Messaging Protocol)
- STOMP là giao thức con (sub-protocol) chạy trên WebSocket, bổ sung ngữ nghĩa gửi nhận tin nhắn:
  - `CONNECT`: Bắt đầu phiên kết nối, gửi kèm Token xác thực.
  - `SUBSCRIBE`: Lắng nghe kênh tin nhắn (`/topic/rooms.{roomId}` hoặc `/topic/admin/inbox`).
  - `SEND`: Gửi tin nhắn đến địa chỉ đích (`/app/chat.sendMessage`).
  - `MESSAGE`: Server chuyển tiếp tin nhắn đến người nhận.

### 14.3 Xác thực Stateless JWT tại `ChannelInterceptor`
- Client gửi Header `Authorization: Bearer <token>` trong frame STOMP `CONNECT`.
- `WebSocketAuthChannelInterceptor` chặn frame, giải mã claims bằng khóa `JWT_SECRET`, tạo `UserPrincipal` và gán vào `accessor.setUser(authentication)`.
- `chat-service` hoàn toàn không cần truy vấn Database của `user-service`, đảm bảo tính mở rộng cao (Stateless Microservices).

### 14.4 Giao tiếp Microservices & Kỹ thuật Token Relay (RestClient)
- Khi Admin mở xem chi tiết phòng chat, `chat-service` cần lấy thêm họ tên, avatar của khách hàng từ `user-service`.
- `chat-service` sử dụng Spring `RestClient` gọi HTTP GET `http://localhost:8081/api/v1/users/{id}`, đồng thời **truyền tiếp (Relay)** token của Admin qua header `Authorization`.
- Có xử lý chịu lỗi (Graceful degradation): Nếu `user-service` tạm thời không phản hồi, `chat-service` dùng thông tin dự phòng chứ không làm sập luồng chat của khách hàng.

### 14.5 Cách mở giao diện Demo để thử nghiệm
1. Khởi động `chat-service` trên cổng `8083`.
2. Mở trình duyệt vào đường dẫn:
   👉 `http://localhost:8083/chat-demo.html`
3. Màn hình chia đôi:
   - Cột trái: Khách hàng (User).
   - Cột phải: Quản trị viên (Admin).
   - Dán token của User và Admin để quan sát tin nhắn nhảy qua lại trong mili-giây!

---

## 15. KIẾN TRÚC & TƯ DUY XÂY DỰNG API GATEWAY (SPRING CLOUD GATEWAY REACTIVE)

### 15.1 Tại sao Hệ thống Microservices BẮT BUỘC phải có API Gateway?
Nếu không có API Gateway, Frontend (Web, Mobile, Postman) phải gọi trực tiếp vào từng service (`user-service:8081`, `chat-service:8083`, `notification-service:8082`). Điều này gây ra 5 vấn đề nghiêm trọng:
1. **Lộ sơ đồ mạng nội bộ (Security Risk):** Toàn bộ các cổng của các service phải mở ra Internet, làm gia tăng bề mặt tấn công (*Attack Surface*).
2. **Ác mộng CORS (Cross-Origin Resource Sharing):** Mỗi service phải tự viết cấu hình CORS riêng, rất dễ xung đột hoặc sơ hở bảo mật.
3. **Xác thực lặp thừa (Redundant Authentication):** Request nào cũng phải đi sâu vào tận thread pool của từng service mới biết token hết hạn -> tốn tài nguyên CPU, Database của service backend.
4. **Không có điểm truy vết tập trung (Distributed Tracing Gap):** Khó đồng bộ `X-Trace-Id` ngay từ lúc request vừa chạm vào hệ thống.
5. **Coupling giữa Client và Backend:** Mỗi khi tách, gộp hoặc đổi port service, toàn bộ ứng dụng Mobile/Frontend phải sửa code và build lại bản cập nhật.

👉 **Giải pháp:** Đặt **API Gateway tại Cổng 8080** làm **Cửa ngõ duy nhất (Single Entry Point)**. Chỉ duy nhất cổng 8080 được public ra ngoài, toàn bộ các microservice còn lại nằm ẩn mình trong mạng nội bộ riêng (Private Network)!

---

### 15.2 Tam giác vàng của Spring Cloud Gateway: Route = Predicate + Filter

Cấu trúc định tuyến của Spring Cloud Gateway luôn xoay quanh 3 khái niệm:
```
                      ┌─────────────────────────────────┐
                      │              ROUTE              │
                      │  (Đường đi hoàn chỉnh cho 1 API)│
                      └────────────────┬────────────────┘
                                       │
                ┌──────────────────────┴──────────────────────┐
                ▼                                             ▼
       ┌─────────────────┐                           ┌─────────────────┐
       │    PREDICATE    │                           │     FILTER      │
       │ (Điều kiện lọc) │                           │  (Bộ biến đổi)  │
       └─────────────────┘                           └─────────────────┘
        - Khớp URL Path                                - Thêm Header
        - Khớp HTTP Method                             - Kiểm tra JWT
        - Khớp Header/Cookie                           - Ghi Log, Đo giờ
```

1. **Route (Tuyến đường):** Đại diện cho một đích đến (ví dụ: route `user-service-users` trỏ tới `http://user-service:8081`).
2. **Predicate (Điều kiện so khớp):** Biểu thức logic kiểm tra xem request đến có thuộc về route này không (ví dụ: `Path=/api/v1/users/**`). Nếu `true`, request sẽ được điều hướng vào route đó.
3. **Filter (Bộ lọc xử lý):** Nơi ta can thiệp vào request trước khi chuyển tiếp (Pre-filter) hoặc can thiệp vào response trước khi trả về cho client (Post-filter).

---

### 15.3 Tư duy Luồng đời của một Request qua Gateway (Request Lifecycle)

```
[ Client Request ]
       │
       ▼ (1. Nhận kết nối tại Netty Event Loop)
┌────────────────────────────────────────────────────────────────────────┐
│                          API GATEWAY (Port 8080)                       │
│                                                                        │
│  [TraceIdGlobalFilter]                                                 │
│    - Kiểm tra hoặc sinh UUID mới cho "X-Trace-Id"                     │
│    - Gắn hook beforeCommit để ghi X-Trace-Id vào HTTP Response         │
│         │                                                              │
│         ▼                                                              │
│  [JwtAuthenticationFilter]                                             │
│    - Nếu là Public URL (Login, Register, Actuator) -> Cho qua          │
│    - Nếu là Protected URL:                                             │
│      + Kiểm tra Header "Authorization: Bearer <token>"                │
│      + Token sai / hết hạn -> Ngắt luồng (Short-circuit), trả HTTP 401 │
│      + Token đúng -> Giải mã Claim, làm giàu (Enrich) Header:          │
│        X-User-Id, X-User-Email, X-User-Roles                           │
│         │                                                              │
│         ▼                                                              │
│  [RequestLoggingGlobalFilter]                                          │
│    - Ghi log START [METHOD] [PATH] [TraceId], bấm giờ System.now()     │
│         │                                                              │
│         ▼ (2. Route & Forward Request qua mạng nội bộ)                 │
└─────────┼──────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────┐
│  user-service    │  (Đọc sẵn X-Trace-Id, X-User-Id, X-User-Roles từ Header)
│   (Port 8081)    │  (Xử lý logic nghiệp vụ và truy vấn Database)
└─────────┬────────┘
          │
          ▼ (3. Trả Response về Gateway)
┌─────────┴──────────────────────────────────────────────────────────────┐
│  [RequestLoggingGlobalFilter] (.then Post-filter hook)                 │
│    - Tính duration = System.now() - startTime                          │
│    - Ghi log END [STATUS] [DURATION ms]                                │
│         │                                                              │
│  [TraceIdGlobalFilter] (beforeCommit hook)                             │
│    - Đính kèm X-Trace-Id vào Response Header                           │
└─────────┬──────────────────────────────────────────────────────────────┘
          │
          ▼ (4. Trả Response chuẩn về cho Client)
[ Client nhận kết quả ]
```

---

### 15.4 Kỹ thuật Reactive Non-blocking: Tại sao KHÔNG ĐƯỢC dùng Spring MVC (Tomcat) trong Gateway?

* **Spring MVC / Tomcat (Thread-per-request):** Mỗi client chiếm trọn 1 Thread của hệ điều hành. Khi có 10.000 request đồng thời, server cần hàng ngàn Thread, dẫn đến tràn bộ nhớ (OOM) và nghẽn CPU do chuyển đổi ngữ cảnh (*Context Switching*).
* **Spring Cloud Gateway / Netty (Event-Loop Non-blocking):** Chỉ cần một số lượng nhỏ Thread (thường bằng số lõi CPU) để điều phối hàng chục nghìn kết nối qua cơ chế I/O bất đồng bộ (*Event Loop*).
* **LƯU Ý SỐNG CÒN:** Không bao giờ đưa dependency `spring-boot-starter-web` vào module Gateway. Nếu đưa vào, Tomcat sẽ được nạp đè lên classpath và làm sập ứng dụng ngay khi khởi động với lỗi:
  `Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway`.

---

### 15.5 Kỹ thuật Header Enrichment & Token Relay

Thay vì để từng microservice tự mình gọi hàm giải mã JWT phức tạp, Gateway đóng vai trò "người gác cổng" thông minh:
1. **Giải mã một lần duy nhất tại Gateway:** Trích xuất `userId`, `email`, `roles`.
2. **Làm giàu Header (Enrichment):** Sử dụng `request.mutate()` để tiêm các Header mới vào HTTP Request trước khi đẩy sang downstream:
   - `X-User-Id`: UUID của người dùng.
   - `X-User-Email`: Email người dùng.
   - `X-User-Roles`: Chuỗi quyền (ví dụ `ROLE_USER,ROLE_ADMIN`).
3. **Truyền tiếp Token gốc (Token Relay):** Giữ nguyên header `Authorization: Bearer ...` để downstream service nếu muốn kiểm tra Zero-Trust hoặc truyền tiếp sang service thứ ba thì vẫn có token nguyên bản.

---

### 15.6 Xử lý Ngoại lệ Toàn cục trong Reactive Gateway (`ErrorWebExceptionHandler`)

Vì Gateway không có `@RestController`, các annotation như `@ControllerAdvice` và `@ExceptionHandler` sẽ **HOÀN TOÀN VÔ DỤNG**.
* Spring Cloud Gateway cung cấp interface `ErrorWebExceptionHandler` với `@Order(-2)`.
* Khi một service backend bị sập (down), Netty sẽ bắn ra ngoại lệ `ConnectException`.
* Filter bắt ngoại lệ này và tự động đóng gói JSON theo đúng chuẩn `ErrorResponse` của `common-lib`:
  ```json
  {
    "timestamp": "2026-10-16T08:30:00Z",
    "status": 503,
    "error": "Service Unavailable",
    "code": "SERVICE_UNAVAILABLE",
    "message": "Dịch vụ đích hiện tại không phản hồi hoặc đang bảo trì. Vui lòng thử lại sau.",
    "path": "/api/v1/users/me",
    "traceId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
  }
  ```
  Giúp Client không bao giờ bị nhận trang trắng HTML lỗi 500 khó hiểu!

---

### 15.7 5 Bước Tư Duy Tự Xây Dựng API Gateway Từ Đầu (Check-list Tự Lập Trình)

Khi bắt đầu một dự án mới hoặc đi phỏng vấn, bạn có thể tự mình viết Gateway hoàn chỉnh chỉ với 5 bước:

1. **Bước 1: Khởi tạo module với dependencies chuẩn:**
   - Parent: Spring Boot 3.4.x
   - Import BOM `spring-cloud-dependencies` (version `2024.0.x`).
   - Thêm `spring-cloud-starter-gateway` (TUYỆT ĐỐI không thêm `spring-boot-starter-web`).
   - Thêm `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (cho bảo mật JWT).
2. **Bước 2: Viết cấu hình định tuyến (`application.yml`):**
   - Đặt `server.port: 8080`.
   - Khai báo các `routes` với `id`, `uri` (trỏ đến host của service), và `predicates: Path=/api/v1/...`.
   - Bật cấu hình `globalcors` để hỗ trợ frontend gọi API.
3. **Bước 3: Lập trình Bộ lọc Trace ID (`TraceIdGlobalFilter`):**
   - Implement `GlobalFilter, Ordered` với `Ordered.HIGHEST_PRECEDENCE`.
   - Lấy hoặc sinh `X-Trace-Id`, mutate vào request và đăng ký `beforeCommit` để gắn vào response.
4. **Bước 4: Lập trình Bộ lọc Bảo mật JWT (`JwtAuthenticationFilter`):**
   - Viết danh sách Whitelist cho các URL công khai (`/api/v1/auth/**`, `/actuator/**`, `/v3/api-docs/**`).
   - Các URL còn lại: kiểm tra Bearer token -> giải mã token bằng Secret Key -> nếu sai trả về 401 JSON -> nếu đúng dùng `request.mutate()` gắn `X-User-Id`, `X-User-Roles`.
5. **Bước 5: Lập trình Bộ xử lý Ngoại lệ (`GatewayExceptionHandler`):**
   - Implement `ErrorWebExceptionHandler` với `@Order(-2)`.
   - Bắt các lỗi kết nối (503 Service Unavailable, 404 Route Not Found) và trả về format JSON chuẩn của hệ thống.

