# BÁO CÁO QUYẾT ĐỊNH VÀ MÔI TRƯỜNG DỰ ÁN (DECISIONS & ENVIRONMENT)

## 1. THÔNG SỐ DỰ ÁN (BƯỚC 0.1)

| Hạng mục | Lựa chọn đã chốt | Ghi chú |
|---|---|---|
| **Tên dự án** | `microservice-platform` | Multi-module Maven root |
| **groupId** | `com.example` | Package prefix: `com.example.<service>` |
| **Java Version** | Java 25 (LTS) | JDK 25 installed |
| **Build Tool** | Maven Multi-Module | Parent `pom.xml` quản lý dependency & version BOM |
| **Framework** | Spring Boot 3.4.3 | Tương thích Java 25 |
| **Database** | PostgreSQL | `user_db`, `notification_db` |
| **Migration** | Flyway | Quản lý schema DB |
| **Cache & Brute-force** | Redis | Cache profile user, đếm login thất bại |
| **Message Broker** | Kafka (KRaft mode) | Topic `user-events`, `user-events.DLT` |
| **Password Hashing** | BCrypt | Spring Security BCryptPasswordEncoder |
| **API Format / Security** | REST / JWT (HS256) | Bearer Token, Rotation Refresh Token |

---

## 2. PHIÊN BẢN MÔI TRƯỜNG HIỆN TẠI (BƯỚC 0.2)

| Công cụ | Phiên bản hiện tại | Đánh giá / Trạng thái |
|---|---|---|
| **Java** | `25` (Oracle JDK 25+37-LTS-3491) | ĐẠT (Đáp ứng yêu cầu ≥ 21) |
| **Maven** | `3.5.4` | ĐẠT |
| **Docker** | `29.8.0` | ĐẠT |
| **Docker Compose** | `v5.5.1` | ĐẠT |
| **Git** | `2.52.0.windows.1` | ĐẠT |

---

## 3. KIẾN TRÚC MỤC TIÊU & MODULE

- `common-lib`: Thư viện dùng chung (`ErrorResponse`, `BusinessException`, `PageResponse`, `EventEnvelope`, DTO Events).
- `user-service`: Service quản lý người dùng, authentication (JWT), cache Redis, transactional outbox sang Kafka.
- `notification-service`: Service lắng nghe Kafka events, idempotency check, gửi email chào mừng qua SMTP (Mailpit).
