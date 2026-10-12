# 🚀 Microservice Platform: User, Notification & Chat System

Hệ thống Microservices hướng sự kiện (**Event-Driven Architecture - EDA**) chuẩn sản xuất (Production-Ready) được xây dựng bằng **Spring Boot 3.4.3**, **Java 21 LTS**, **Apache Kafka**, **PostgreSQL**, **Redis**, và **Docker Compose**.

Hệ thống bao gồm các dịch vụ độc lập:
* **`user-service`** (Port `8081`): Quản lý người dùng, hồ sơ cá nhân, phân quyền RBAC, xác thực JWT (Access Token 15m + Refresh Token Rotation 7d), bộ nhớ đệm Redis, và phát sự kiện qua **Transactional Outbox Pattern**.
* **`notification-service`** (Port `8082`): Lắng nghe sự kiện Kafka (`user-events`), đảm bảo tính **Idempotent** chống xử lý trùng, cơ chế Retry & **Dead Letter Topic (DLT)**, và gửi email chào mừng qua Mailpit SMTP.
* **`chat-service`** (Port `8083`): Quản lý phòng chat và tin nhắn thời gian thực qua giao thức **WebSocket STOMP**, xác thực Token tại kênh Handshake Channel Interceptor.
* **`common-lib`**: Thư viện dùng chung chứa DTOs, Event Envelopes (`EventEnvelope<T>`), chuẩn phản hồi API (`ErrorResponse`, `PageResponse`) và ngoại lệ nghiệp vụ (`BusinessException`).

---

## 🏛️ Sơ Đồ Kiến Trúc Hệ Thống (Architecture)

```
[ Client / Web / Postman / Mobile ]
               │
        HTTPS REST / WebSocket
               ▼
  ┌───────────────────────────────┐
  │         user-service          │ ────▶ PostgreSQL (user_db :5433)
  │          (Port 8081)          │ ────▶ Redis Cache & Rate-limit (:6379)
  └───────────────┬───────────────┘
                  │  (Transactional Outbox Pattern - At-least-once)
                  ▼
      [ Apache Kafka (KRaft :9092) ]
                  │
        topic: 'user-events'
                  │
                  ▼
  ┌───────────────────────────────┐
  │     notification-service      │ ────▶ PostgreSQL (notification_db :5433)
  │          (Port 8082)          │ ────▶ Mailpit SMTP Mock Email (:1025)
  └───────────────────────────────┘
                  │ (Poison Message Fallback)
                  ▼
     [ topic: 'user-events.DLT' ]

  ┌───────────────────────────────┐
  │         chat-service          │ ────▶ PostgreSQL (chat_db :5433)
  │   (Port 8083 - STOMP /ws/chat)│
  └───────────────────────────────┘
```

> 📖 **Xem chi tiết tài liệu kiến trúc kỹ thuật:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)  
> 📖 **Xem quy trình vận hành và xử lý sự cố:** [docs/RUNBOOK.md](docs/RUNBOOK.md)

---

## 📌 Bảng Cổng & Đường Dẫn Kết Nối (Port Mapping)

| Dịch vụ / Container | Cổng Host | Cổng Container | Mục đích / Đường dẫn |
| :--- | :--- | :--- | :--- |
| **user-service** | `8081` | `8081` | REST API, Swagger: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html) |
| **notification-service** | `8082` | `8082` | REST API, Swagger: [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html) |
| **chat-service** | `8083` | `8083` | REST API, Swagger: [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html), WS: `/ws/chat` |
| **PostgreSQL 16** | `5433` | `5432` | `user_db`, `notification_db`, `chat_db` |
| **Redis 7** | `6379` | `6379` | Cache & Rate Limiting (`redis_dev_pass`) |
| **Apache Kafka (KRaft)** | `9092` | `9092` | Message Broker: `localhost:9092` |
| **Mailpit Web UI** | `8025` | `8025` | Hòm thư giao diện Web: [http://localhost:8025](http://localhost:8025) |
| **Mailpit SMTP** | `1025` | `1025` | Cổng gửi email SMTP |

> **LƯU Ý VỀ CỔNG POSTGRESQL (5433):**  
> Cổng PostgreSQL trên máy Host được cấu hình là **`5433`** để tránh xung đột với các phiên bản PostgreSQL chạy cổng mặc định `5432` trên máy của bạn.

---

## ⚡ Hướng Dẫn Khởi Chạy Nhanh (≤ 15 Phút)

### 1. Yêu cầu môi trường
* **Docker & Docker Compose**: Đã cài đặt và đang chạy Docker Desktop.
* **Java SDK**: Bản 21 LTS trở lên.
* **Git**: Đã cài đặt.

### 2. Thiết lập cấu hình môi trường
Tạo file cấu hình bí mật `.env` từ file mẫu:
* **Windows (PowerShell / Command Prompt):**
  ```powershell
  copy .env.example .env
  ```
* **Linux / macOS:**
  ```bash
  cp .env.example .env
  ```

### 3. Khởi chạy toàn bộ hệ thống bằng 1 lệnh duy nhất
Tại thư mục gốc dự án:
```powershell
docker compose up -d --build
```
*Lệnh trên sẽ tự động khởi động 4 dịch vụ hạ tầng (Postgres, Redis, Kafka, Mailpit) và build image, khởi chạy cả 3 microservice.*

Kiểm tra trạng thái các container:
```powershell
docker compose ps
```
*Tất cả container đều phải hiển thị `healthy` hoặc `Up`.*

---

## 🧪 Kiểm Thử Hệ Thống (Testing)

### 1. Chạy toàn bộ Unit & Integration Test (Testcontainers)
```powershell
# Chạy toàn bộ test suite trên toàn bộ 4 module
./mvnw.cmd clean test

# Hoặc trên Linux / macOS:
./mvnw clean test
```

### 2. Chạy kịch bản kiểm thử End-to-End tự động (E2E Test)
Dự án cung cấp sẵn kịch bản PowerShell tự động hóa 8 bước E2E nghiêm ngặt:
1. Đăng ký tài khoản người dùng mới (`POST /api/v1/users`).
2. Kiểm tra email chào mừng được gửi tới Mailpit (`http://localhost:8025/api/v1/messages`).
3. Đăng nhập hệ thống bằng mật khẩu vừa tạo (`POST /api/v1/auth/login`).
4. Xem thông tin hồ sơ cá nhân (`GET /api/v1/users/me`) kèm xác thực Bearer token.
5. Đổi mật khẩu (`PUT /api/v1/users/me/password`).
6. Đăng nhập lại bằng mật khẩu mới.
7. Đăng xuất thu hồi token (`POST /api/v1/auth/logout`).
8. Kiểm tra từ chối refresh token đã bị thu hồi (`POST /api/v1/auth/refresh`).

Chạy lệnh:
```powershell
powershell -ExecutionPolicy Bypass -File docs/test-e2e.ps1
```
*Kết quả: Toàn bộ 8/8 bước pass màu xanh lá (PASSED 100%).*

### 3. Kiểm thử Distributed Tracing (Trace ID)
Kiểm tra khả năng theo vết xuyên suốt qua User Service, Kafka và Notification Service:
```powershell
powershell -ExecutionPolicy Bypass -File docs/test-trace.ps1
```

---

## 📊 Quan Sát Hệ Thống (Observability & Actuator)

Mỗi service cung cấp đầy đủ các cổng giám sát chuẩn Spring Boot Actuator:

* **Sức khỏe dịch vụ (Health Check):**
  - `user-service`: [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
  - `notification-service`: [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health)
  - `chat-service`: [http://localhost:8083/actuator/health](http://localhost:8083/actuator/health)
* **Số liệu hiệu năng (Metrics):**
  - `user-service`: [http://localhost:8081/actuator/metrics](http://localhost:8081/actuator/metrics)
  - `notification-service`: [http://localhost:8082/actuator/metrics](http://localhost:8082/actuator/metrics)
* **Định dạng Log với Distributed Trace ID:**
  ```
  2026-10-12 17:11:27.399 INFO [scheduling-1] [trace-e2e-1621552424] c.e.u.scheduler.OutboxEventProcessor : Phát sự kiện Outbox lên Kafka thành công: traceId [trace-e2e-1621552424]
  2026-10-12 17:11:28.850 INFO [ntainer#0-0-C-1] [trace-e2e-1621552424] c.e.n.service.impl.EmailServiceImpl : Gửi email chào mừng thành công tới [trace_demo@example.com]
  ```

---

## 🔒 Danh Mục An Toàn & Bảo Mật Đã Triển Khai (Security Highlights)

1. **Không lưu trữ Secret trong Git**: Toàn bộ mật khẩu, thông tin kết nối và khóa JWT Secret được tách biệt hoàn toàn vào `.env` (file mẫu `.env.example`).
2. **Mật khẩu & Token**: Mật khẩu băm an toàn qua `BCryptPasswordEncoder` (rounds 12). Refresh Token được băm SHA-256 trong Database và tự động xoay vòng (Rotation) khi sử dụng.
3. **Bảo mật phản hồi xác thực**: Lỗi đăng nhập trả về thông báo chung để chống tấn công dò tìm tài khoản (User Enumeration).
4. **Phân vùng dữ liệu an toàn**: Mỗi microservice sở hữu cơ sở dữ liệu riêng biệt (`user_db`, `notification_db`, `chat_db`), kết nối độc lập qua tài khoản người dùng DB riêng.
5. **Docker Non-Root Security**: Mọi Dockerfile đều cấu hình chạy dưới người dùng bị giới hạn đặc quyền `appuser` (UID 1001).
6. **Bảo vệ XSS & Injection**: Escape ký tự HTML trên template email (`HtmlUtils.htmlEscape`) và validate input qua Jakarta Validation.
