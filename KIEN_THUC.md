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
9. [HƯỚNG DẪN CHẠY DỰ ÁN TRÊN LAPTOP Ở NHÀ](#9-hướng-dẫn-chạy-dự-án-trên-laptop-ở-nhà)

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

## 9. HƯỚNG DẪN CHẠY DỰ ÁN TRÊN LAPTOP Ở NHÀ

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
