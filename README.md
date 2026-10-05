# 🚀 Microservice Platform: User & Notification System

Hệ thống Microservices hướng sự kiện (Event-Driven Architecture) gồm 2 dịch vụ độc lập:
* **`user-service`** (Port `8081`): Quản lý người dùng, hồ sơ cá nhân, phân quyền, xác thực (JWT), bộ nhớ đệm (Redis), phát sự kiện qua mô hình Transactional Outbox.
* **`notification-service`** (Port `8082`): Nhận sự kiện từ Apache Kafka (`user-events`), đảm bảo tính Idempotent chống xử lý trùng và gửi email chào mừng qua Mailpit SMTP.
* **`common-lib`**: Thư viện dùng chung chứa DTOs, Event Envelopes, mã phản hồi chuẩn (`ErrorResponse`, `PageResponse`) và các Ngoại lệ nghiệp vụ (`BusinessException`).

---

## 🏛️ Sơ đồ Kiến trúc Hệ thống

```
[ Client / Postman / Web ]
             │
        HTTPS / REST
             ▼
     ┌───────────────┐
     │ user-service  │ ────▶ PostgreSQL (user_db trên cổng 5433)
     │ (Port 8081)   │ ────▶ Redis (Cache & chống Brute-force cổng 6379)
     └───────┬───────┘
             │  (Transactional Outbox Pattern)
             ▼
       [ Apache Kafka: topic 'user-events' ]
             │
             ▼
     ┌──────────────────────┐
     │ notification-service │ ────▶ PostgreSQL (notification_db trên cổng 5433)
     │ (Port 8082)          │ ────▶ Mailpit SMTP Mock Email (Web UI cổng 8025)
     └──────────────────────┘
```

---

## 📋 Yêu cầu Hệ thống

Trước khi chạy, máy tính của bạn cần cài đặt:
* **Hệ điều hành**: Windows 10/11, macOS hoặc Linux.
* **Docker & Docker Compose**: Bản mới nhất (khuyến nghị Docker Desktop).
* **Java SDK**: Bản 21 LTS trở lên (hoặc Java 25).
* **Git**: Để quản lý và đồng bộ mã nguồn.

---

## ⚡ Hướng dẫn Khởi chạy Từng Bước (Quick Start)

### Bước 1: Tải mã nguồn về máy
```bash
git clone https://github.com/<tai-khoan-cua-ban>/<ten-repo>.git
cd <ten-repo>
```

### Bước 2: Thiết lập biến môi trường
Tạo file `.env` từ file mẫu `.env.example`:
* **Trên Windows (Command Prompt / PowerShell):**
  ```powershell
  copy .env.example .env
  ```
* **Trên macOS / Linux:**
  ```bash
  cp .env.example .env
  ```

### Bước 3: Khởi động Hạ tầng Docker (PostgreSQL, Redis, Kafka, Mailpit)
Tại thư mục gốc dự án, chạy lệnh:
```bash
docker compose -f docker-compose.infra.yml up -d
```

Kiểm tra trạng thái các container (phải có chữ `healthy` hoặc `running`):
```bash
docker compose -f docker-compose.infra.yml ps
```

| Dịch vụ | Địa chỉ cổng (Host Port) | Thông tin kết nối |
| :--- | :--- | :--- |
| **PostgreSQL** | `localhost:5433` | User: `postgres` / Mật khẩu: trong `.env` |
| **Redis** | `localhost:6379` | Mật khẩu: `redis_dev_pass` |
| **Kafka (KRaft)** | `localhost:9092` | Bootstrap server: `localhost:9092` |
| **Mailpit Web UI** | `http://localhost:8025` | Giao diện hòm thư xem email test |
| **Mailpit SMTP** | `localhost:1025` | Cổng gửi mail SMTP cho backend |

> **LƯU Ý QUAN TRỌNG VỀ CỔNG POSTGRESQL (5433):**  
> Dự án map cổng PostgreSQL ra ngoài máy host là **`5433`** (thay vì `5432` mặc định) để tránh xung đột với các phiên bản PostgreSQL đã cài sẵn trên máy tính Windows.

---

### Bước 4: Khởi chạy Ứng dụng Backend

Bạn có thể chọn 1 trong 2 cách sau:

#### CÁCH A: Chạy trực tiếp bằng Maven Wrapper (Khuyên dùng khi Dev)
* **Chạy toàn bộ bài kiểm thử Unit Test:**
  ```powershell
  # Windows
  ./mvnw.cmd clean test

  # Linux / macOS
  ./mvnw clean test
  ```

* **Khởi động `user-service`:**
  ```powershell
  # Windows
  ./mvnw.cmd -pl user-service spring-boot:run

  # Linux / macOS
  ./mvnw -pl user-service spring-boot:run
  ```

#### CÁCH B: Đóng gói và chạy bằng Docker Container
Dự án đã có sẵn Dockerfile tối ưu Multi-stage build với Eclipse Temurin JRE 21:
```powershell
# 1. Đóng gói ứng dụng thành file jar
./mvnw.cmd -pl user-service clean package -DskipTests

# 2. Xây dựng Docker Image (Kích thước nén chỉ ~99MB)
docker build -t user-service:latest -f user-service/Dockerfile .

# 3. Chạy container user-service gắn vào mạng hạ tầng
docker run -d --name user-service -p 8081:8081 --network microservice-network user-service:latest
```

---

## 🌐 Các Đường Dẫn Kiểm Tra Dịch Vụ

Khi `user-service` khởi chạy thành công trên cổng `8081`:

* 📖 **Tài liệu API tương tác trực tiếp (Swagger UI):**  
  👉 [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
* 📑 **Tài liệu OpenAPI dạng JSON (OpenAPI Spec):**  
  👉 [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs)
* 💓 **Kiểm tra trạng thái sức khỏe dịch vụ (Actuator Health):**  
  👉 [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
* 📬 **Hộp thư xem Email chào mừng giả lập (Mailpit Web UI):**  
  👉 [http://localhost:8025](http://localhost:8025)

---

## 🛠️ Gọi Thử API Mẫu (cURL)

### 1. Đăng ký tài khoản mới (`POST /api/v1/users`)
```bash
curl -X POST http://localhost:8081/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePassword123@",
    "fullName": "Nguyen Van A",
    "phone": "0987654321"
  }'
```
* **Kết quả trả về:** `HTTP 201 Created` kèm Header `Location` và thông tin User Profile (không chứa mật khẩu).

### 2. Xem hồ sơ cá nhân (`GET /api/v1/users/me`)
```bash
curl -X GET http://localhost:8081/api/v1/users/me \
  -H "X-User-Id: <UUID_CUA_BAN>"
```
* **Kết quả trả về:** `HTTP 200 OK` với thông tin cá nhân.

### 3. Đổi mật khẩu (`PUT /api/v1/users/me/password`)
```bash
curl -X PUT http://localhost:8081/api/v1/users/me/password \
  -H "X-User-Id: <UUID_CUA_BAN>" \
  -H "Content-Type: application/json" \
  -d '{
    "oldPassword": "SecurePassword123@",
    "newPassword": "NewPassword456@"
  }'
```
* **Kết quả trả về:** `HTTP 204 No Content`.

---

## ❓ Xử Lý Sự Cố Thường Gặp (Troubleshooting)

1. **Lỗi `Docker daemon is not running`:**  
   ➡️ Hãy đảm bảo bạn đã bật ứng dụng Docker Desktop trên máy trước khi chạy lệnh docker compose.
2. **Lỗi `Address already in use: bind` (Trùng cổng):**  
   ➡️ Kiểm tra xem cổng `5433`, `6379`, `8081` hoặc `8025` có ứng dụng nào khác đang chiếm giữ không.
3. **Xem toàn bộ lý thuyết và giải thích chi tiết:**  
   ➡️ Đọc file [KIEN_THUC.md](KIEN_THUC.md) để ôn tập về kiến trúc, bảo mật BCrypt, `@Transactional`, `GlobalExceptionHandler`, và các mẫu thiết kế.
4. **Xem kế hoạch lộ trình từng bước:**  
   ➡️ Đọc file [KE_HOACH_USER_NOTIFICATION.md](KE_HOACH_USER_NOTIFICATION.md).
