# Microservice Platform: User & Notification System

Hệ thống microservice bao gồm 2 service chính:
- **`user-service`**: Quản lý người dùng, authentication (JWT), caching (Redis), phát sự kiện qua Transactional Outbox.
- **`notification-service`**: Nhận sự kiện từ Kafka (`user-events`), kiểm tra idempotency và gửi email chào mừng qua Mailpit SMTP.
- **`common-lib`**: Thư viện chứa DTOs, Envelopes, Exceptions & Error Responses dùng chung.

## Cấu trúc thư mục

```
microservice-platform/
├── pom.xml                       # Parent POM
├── README.md                     # Tài liệu hướng dẫn
├── .env.example                  # Mẫu biến môi trường
├── docs/                         # Tài liệu thiết kế & quyết định
├── common-lib/                   # Module thư viện dùng chung
├── user-service/                 # Service Quản lý user & Auth
└── notification-service/         # Service Thông báo (gửi Email)
```

## Yêu cầu môi trường

- **Java**: 25 (hoặc 21 LTS+)
- **Maven**: 3.8+
- **Docker & Docker Compose**: Bản mới nhất

## Hướng dẫn chạy nhanh (Development)

1. Sao chép biến môi trường:
   ```bash
   cp .env.example .env
   ```
2. Chạy hạ tầng:
   ```bash
   docker compose -f docker-compose.infra.yml up -d
   ```
3. Build dự án:
   ```bash
   mvn clean install
   ```
