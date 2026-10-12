# KẾ HOẠCH XÂY DỰNG: USER-SERVICE (GỘP AUTH) + NOTIFICATION-SERVICE

> **Phạm vi hiện tại:** chỉ 2 microservice — `user-service` (quản lý người dùng **và** xác thực/đăng nhập) và `notification-service` (nghe sự kiện, gửi email). **Không** có `auth-service` riêng, **không** có gateway/Eureka/product/order ở giai đoạn này.
>
> **Cách dùng:** đưa file này cho AI ở đầu phiên, dán Prompt khởi đầu (Mục 11.1), rồi giao **từng bước một** theo mẫu ở Mục 11.2.
>
> File này thay thế bản `KE_HOACH_MICROSERVICE.md` trước đó (bản đó là lộ trình đầy đủ 8 service; có thể giữ làm tham khảo để mở rộng sau).

---

## 0. QUY TẮC BẮT BUỘC DÀNH CHO AI

1. **Làm đúng một bước mỗi lần.** Không làm trước, không thêm tính năng ngoài kế hoạch.
2. **Không bịa.** Không tự đặt tên thư viện/phiên bản không có thật. Quản lý phiên bản bằng BOM của Spring Boot; chỗ nào không chắc, ghi "cần xác minh" và hỏi.
3. **Mỗi service độc lập:** database riêng, **không** truy cập DB của service kia. Hai service chỉ giao tiếp bằng **Kafka** (bất đồng bộ).
4. **Giữ đúng cấu trúc package** ở Mục 3.2.
5. **Không hard-code bí mật** (mật khẩu DB, JWT secret, SMTP). Dùng biến môi trường; có `.env.example`, không commit `.env`.
6. **Mọi API:** validate đầu vào, dùng DTO (không trả Entity), lỗi trả theo định dạng chuẩn (Mục 3.4), có OpenAPI.
7. **Mọi bước có test và test phải pass** trước khi báo xong. Không xóa hay tắt test để "cho qua".
8. **Báo cáo cuối mỗi bước** theo mẫu Mục 11.2.
9. **Commit nhỏ, rõ nghĩa** (Conventional Commits: `feat:`, `fix:`, `test:`, `chore:`, `docs:`).
10. **Không xóa/ghi đè** file có sẵn khi chưa được đồng ý; khi sửa file cũ phải nêu rõ sửa gì.
11. Tên class/biến/API bằng tiếng Anh; comment và tài liệu bằng tiếng Việt.
12. Gặp lỗi: đọc log, tìm nguyên nhân gốc rồi sửa; không đoán mò.

---

## 1. MỤC TIÊU

Hệ thống gồm 2 service chạy bằng `docker compose up`:

| Thành phần | Vai trò |
|-----------|---------|
| `user-service` | Đăng ký, đăng nhập (JWT access + refresh), đăng xuất, profile, đổi mật khẩu, quản trị user (ADMIN), cache Redis, phát sự kiện |
| `notification-service` | Nghe sự kiện từ Kafka, gửi email (chào mừng; mở rộng: OTP/đặt lại mật khẩu) |
| `common-lib` | Thư viện nhỏ dùng chung: định dạng lỗi, envelope & DTO sự kiện |
| Hạ tầng | PostgreSQL (2 DB), Redis, Kafka, Mailpit (SMTP giả lập khi dev) |

**Định nghĩa "xong":** đăng ký user → email chào mừng xuất hiện trong Mailpit → đăng nhập nhận token → gọi API profile bằng token; có test tự động, log có `traceId`, health check, tài liệu chạy dự án.

---

## 2. CÔNG NGHỆ (CHỐT Ở BƯỚC 0.1)

| Hạng mục | Lựa chọn mặc định | Ghi chú |
|----------|-------------------|---------|
| Ngôn ngữ | Java LTS (tối thiểu 21) | Xác minh ở BƯỚC 0.2 |
| Framework | Spring Boot 4.x (bản ổn định mới nhất trên start.spring.io, từ 4.1 trở lên) | Dùng BOM. Không chọn 3.x vì 3.5 đã hết hỗ trợ OSS (30/06/2026) |
| Build | Maven multi-module | Có thể đổi sang Gradle |
| DB | PostgreSQL, mỗi service một DB (`user_db`, `notification_db`) | |
| Migration | Flyway | Không dùng `ddl-auto=update` |
| Cache / đếm đăng nhập sai | Redis | |
| Message broker | Kafka (KRaft) | Có thể thay RabbitMQ nếu muốn |
| Bảo mật | Spring Security + JWT (HS256, secret ≥ 256 bit lấy từ biến môi trường) + BCrypt | Có thể nâng lên RS256/Keycloak sau |
| Mapping | MapStruct | |
| API docs | springdoc-openapi | |
| Email | Spring Mail + Thymeleaf; Mailpit khi dev | |
| Test | JUnit 5, Mockito, Testcontainers | |
| Container | Docker, Docker Compose | |
| CI | GitHub Actions | |

> **Vì sao gộp auth vào user-service:** với 2 service, tách auth chỉ thêm độ phức tạp (thêm DB, thêm đồng bộ credential) mà chưa có lợi ích. Thiết kế dưới đây vẫn giữ lớp `AuthService` tách biệt trong code để sau này muốn tách ra `auth-service` thì chuyển dễ.

---

## 3. KIẾN TRÚC & QUY ƯỚC

### 3.1 Kiến trúc

```
[ Client ] ──HTTPS/REST──▶ user-service ──▶ PostgreSQL (user_db)
                               │  └──────▶ Redis (cache, đếm login sai)
                               │
                               └─ Outbox ─▶ Kafka  topic: user-events
                                                │
                                                ▼
                                       notification-service ──▶ SMTP (Mailpit)
                                                │
                                                └──▶ PostgreSQL (notification_db)
```

Quyết định đã chốt:
- **Transactional Outbox:** user-service ghi sự kiện vào bảng `outbox_events` **cùng transaction** với dữ liệu, rồi một job phát lên Kafka. Không gọi Kafka trực tiếp trong `@Transactional`.
- **Consumer idempotent:** notification-service lưu `eventId` đã xử lý để không gửi email trùng.
- **Không có đường gọi đồng bộ** giữa 2 service.

### 3.2 Cấu trúc thư mục

```
microservice-platform/
├── pom.xml                       # parent POM
├── README.md
├── .env.example
├── docker-compose.infra.yml      # chỉ hạ tầng (dev local)
├── docker-compose.yml            # hạ tầng + 2 service
├── infra/postgres/init/          # tạo user_db, notification_db
├── common-lib/
├── user-service/
├── notification-service/
├── docs/
└── .github/workflows/ci.yml
```

Cấu trúc package mỗi service (đổi `com.example` theo `groupId` thật):

```
src/main/java/com/example/<service>/
├── config/        # Security, OpenAPI, Redis, Kafka, Mail...
├── controller/    # chỉ nhận request, gọi service, trả DTO
├── dto/{request,response}/
├── service/ + service/impl/
├── repository/
├── entity/
├── mapper/        # MapStruct
├── event/         # Outbox publisher / Kafka consumer
├── security/      # (user-service) JWT filter, JwtService, UserDetails
├── exception/     # GlobalExceptionHandler + exception nghiệp vụ
└── util/
src/main/resources/
├── application.yml, application-dev.yml, application-docker.yml
└── db/migration/  # V1__..., V2__...
```

### 3.3 Quy ước API

- Prefix `/api/v1`. ID dạng UUID. Thời gian ISO-8601 UTC.
- Phân trang `?page=0&size=20&sort=createdAt,desc` → trả `PageResponse<T>` (`content`, `page`, `size`, `totalElements`, `totalPages`).
- Mã HTTP: `201` tạo mới, `204` xóa, `400` validation, `401` chưa xác thực, `403` thiếu quyền, `404` không thấy, `409` trùng (email), `423` tài khoản bị khóa, `429` đăng nhập sai quá nhiều.

### 3.4 Định dạng lỗi chuẩn (trong `common-lib`)

```json
{
  "timestamp": "2026-10-05T07:30:00Z",
  "status": 409,
  "error": "CONFLICT",
  "code": "USER_EMAIL_EXISTS",
  "message": "Email đã được sử dụng",
  "path": "/api/v1/users",
  "traceId": "a1b2c3...",
  "details": [{ "field": "email", "message": "đã tồn tại" }]
}
```

### 3.5 Quy ước Kafka

- Topic: `user-events` và `user-events.DLT` (dead-letter). Key message = `userId` (giữ thứ tự theo user).
- Envelope chuẩn:

```json
{
  "eventId": "uuid",
  "eventType": "UserCreatedEvent",
  "version": 1,
  "occurredAt": "2026-10-05T07:30:00Z",
  "source": "user-service",
  "payload": { "userId": "uuid", "email": "a@b.com", "fullName": "Nguyễn Văn A" }
}
```

- Payload **không** chứa mật khẩu/hash/token đăng nhập.

---

## 4. ĐẶC TẢ

### 4.1 `user-service` (đã gộp auth)

**Bảng**

| Bảng | Cột chính |
|------|-----------|
| `users` | `id` UUID PK, `email` unique (không phân biệt hoa thường), `password_hash`, `full_name`, `phone`, `avatar_url`, `status` (`ACTIVE`/`LOCKED`/`DELETED`), `created_at`, `updated_at`, `version` (optimistic lock) |
| `roles` | `id`, `name` unique (`ROLE_USER`, `ROLE_ADMIN`) |
| `user_roles` | `user_id`, `role_id` |
| `refresh_tokens` | `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at`, `created_at`, `replaced_by` |
| `outbox_events` | `id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload` jsonb, `created_at`, `published_at` null |

**Endpoint**

| Method | Path | Quyền | Mô tả |
|--------|------|-------|-------|
| POST | `/api/v1/users` | public | Đăng ký (email, password, fullName) |
| POST | `/api/v1/auth/login` | public | Trả `accessToken` (15 phút) + `refreshToken` (7 ngày) |
| POST | `/api/v1/auth/refresh` | public | Đổi refresh token lấy cặp token mới (rotation: token cũ bị thu hồi) |
| POST | `/api/v1/auth/logout` | USER | Thu hồi refresh token |
| GET | `/api/v1/users/me` | USER | Xem profile |
| PUT | `/api/v1/users/me` | USER | Sửa profile |
| PUT | `/api/v1/users/me/password` | USER | Đổi mật khẩu (thu hồi mọi refresh token cũ) |
| GET | `/api/v1/users` | ADMIN | Danh sách + tìm kiếm + phân trang |
| GET | `/api/v1/users/{id}` | ADMIN | Xem theo id |
| PATCH | `/api/v1/users/{id}/status` | ADMIN | Khóa/mở khóa |
| DELETE | `/api/v1/users/{id}` | ADMIN | Xóa mềm |

JWT claim: `sub` (userId), `email`, `roles`, `iat`, `exp`, `jti`.

**Quy tắc nghiệp vụ**
- Email duy nhất, chuẩn hóa chữ thường; mật khẩu ≥ 8 ký tự, có chữ và số; băm BCrypt; không bao giờ trả `password_hash`.
- Xóa mềm (`status = DELETED`), user `LOCKED`/`DELETED` không đăng nhập được.
- Đăng nhập sai ≥ 5 lần trong 15 phút (đếm bằng Redis theo email) → từ chối tạm thời (`429`).
- Thông báo lỗi đăng nhập **không** tiết lộ email có tồn tại hay không.
- Cache profile theo `userId` (TTL 10 phút), xóa cache khi cập nhật/xóa/khóa.

**Sự kiện phát ra (topic `user-events`):** `UserCreatedEvent`, `UserUpdatedEvent`, `UserDeletedEvent`.

### 4.2 `notification-service`

- Consumer `user-events`: `UserCreatedEvent` → email chào mừng. Các loại sự kiện khác → bỏ qua có log (không lỗi).
- Bảng `processed_events` (`event_id` PK, `processed_at`) để chống xử lý trùng; bảng `notification_log` (`id`, `event_id`, `recipient`, `type`, `status`, `error`, `created_at`).
- Gửi email qua SMTP (Mailpit khi dev), template Thymeleaf (tiếng Việt, escape đúng).
- Lỗi tạm thời → retry có backoff; vượt ngưỡng → đẩy sang `user-events.DLT`.
- Port 8082; có `/actuator/health`; **không** cần API nghiệp vụ.

### 4.3 Mở rộng (chỉ làm khi người dùng yêu cầu, sau BƯỚC 6)

- **Quên mật khẩu / OTP:** `POST /api/v1/auth/forgot-password` → sự kiện `PasswordResetRequestedEvent` → notification gửi mã. Chỉ lưu **hash** của mã trong DB, mã có hạn 10 phút, dùng một lần, giới hạn số lần thử.
- **Xác thực email:** gửi liên kết/mã xác thực sau đăng ký.

---

## 5. LỘ TRÌNH TỪNG BƯỚC

Mỗi bước: **Mục tiêu → Việc cần làm → DoD (tiêu chí hoàn thành) → Kiểm tra.** Chỉ báo "xong" khi **mọi DoD đều đạt**.

### PHASE 0 — CHUẨN BỊ

#### BƯỚC 0.1 — Chốt thông số
- **Việc cần làm:** xác nhận với người dùng: `groupId`, tên dự án, Java version, Maven/Gradle, Kafka hay RabbitMQ, mật khẩu băm BCrypt hay Argon2.
- **DoD:** ghi vào `docs/DECISIONS.md`.

#### BƯỚC 0.2 — Kiểm tra môi trường
- **Việc cần làm:** chạy và ghi lại `java -version`, `mvn -v`, `docker -v`, `docker compose version`, `git --version`; tra tài liệu chính thức để xác định phiên bản Spring Boot ổn định mới nhất.
- **DoD:** bảng phiên bản trong `docs/DECISIONS.md`; công cụ thiếu thì hướng dẫn cài.

#### BƯỚC 0.3 — Khởi tạo repo & parent POM
- **Việc cần làm:** `git init`; parent `pom.xml` (packaging `pom`, import BOM Spring Boot, khai báo 3 module: `common-lib`, `user-service`, `notification-service`); `.gitignore`; `.env.example`; `README.md` khung.
- **DoD:** `mvn -q validate` thành công; cấu trúc khớp Mục 3.2.

---

### PHASE 1 — HẠ TẦNG

#### BƯỚC 1.1 — Docker Compose hạ tầng
- **Việc cần làm:** `docker-compose.infra.yml` gồm PostgreSQL (script tạo `user_db`, `notification_db`, mỗi DB một user riêng), Redis, Kafka (KRaft), Mailpit; có `healthcheck`, volume, network; mật khẩu lấy từ `.env`.
- **DoD:** `docker compose -f docker-compose.infra.yml up -d` → mọi container `healthy`; kết nối được từ máy local.
- **Kiểm tra:** `docker compose ps`; `psql` vào 2 DB; `redis-cli ping`; mở Mailpit UI (`:8025`).

---

### PHASE 2 — COMMON-LIB

#### BƯỚC 2.1 — `common-lib`
- **Việc cần làm:** `ErrorResponse` (Mục 3.4), `BusinessException` và vài lỗi cơ bản, `PageResponse<T>`, `EventEnvelope<T>` (Mục 3.5), DTO sự kiện (`UserCreatedEvent`, `UserUpdatedEvent`, `UserDeletedEvent`). Không chứa logic riêng của service nào, không kéo dependency nặng.
- **DoD:** build ra jar, hai service dùng được; có unit test cơ bản.
- **Kiểm tra:** `mvn -pl common-lib test install`.

---

### PHASE 3 — USER-SERVICE

#### BƯỚC 3.1 — Khung service & kết nối DB
- **Việc cần làm:** module `user-service` (port `8081`); dependency: web, validation, data-jpa, postgresql, flyway, actuator, springdoc, lombok, mapstruct, `common-lib`; `application.yml` đọc cấu hình từ biến môi trường; migration `V1__init.sql` tạo `users`, `roles`, `user_roles` + seed 2 role.
- **DoD:** service khởi động, Flyway chạy thành công, `/actuator/health` = `UP`.
- **Kiểm tra:** chạy service; xem bảng trong `user_db`.

#### BƯỚC 3.2 — Entity, Repository, DTO, Mapper
- **Việc cần làm:** `User`, `Role` (có `@Version`, auditing `createdAt/updatedAt`); `UserRepository` (`findByEmailIgnoreCase`, `existsByEmailIgnoreCase`, truy vấn tìm kiếm phân trang); DTO request/response; `UserMapper` (MapStruct); annotation validation.
- **DoD:** test repository (`@DataJpaTest` + Testcontainers PostgreSQL) pass.
- **Kiểm tra:** `mvn -pl user-service test`.

#### BƯỚC 3.3 — Service layer: đăng ký & profile
- **Việc cần làm:** `UserService`/`UserServiceImpl`: đăng ký (kiểm tra trùng email → BCrypt → lưu → gán `ROLE_USER`), xem/sửa profile, đổi mật khẩu (xác minh mật khẩu cũ), tìm kiếm, khóa/mở khóa, xóa mềm. `@Transactional` đúng chỗ.
- **DoD:** unit test (Mockito) phủ ≥ 80% lớp service, gồm trường hợp lỗi (trùng email, không tồn tại, sai mật khẩu cũ).
- **Kiểm tra:** `mvn -pl user-service test` + báo cáo JaCoCo.

#### BƯỚC 3.4 — Controller & xử lý lỗi
- **Việc cần làm:** `UserController` (các endpoint `/users/**` ở Mục 4.1); `GlobalExceptionHandler` theo Mục 3.4; OpenAPI có mô tả và ví dụ.
- **DoD:** test `@WebMvcTest` cho từng endpoint (thành công + lỗi); Swagger UI truy cập được.
- **Kiểm tra:** gọi thử bằng `curl`/Postman, đối chiếu mã HTTP.

#### BƯỚC 3.5 — Xác thực: JWT, login, refresh, logout
- **Việc cần làm:**
  - `JwtService` (tạo/kiểm tra token, secret từ biến môi trường), `JwtAuthenticationFilter`, cấu hình Spring Security (stateless).
  - `AuthController` + `AuthService`: `login`, `refresh` (rotation, lưu **hash** refresh token vào bảng `refresh_tokens` — migration `V2`), `logout`.
  - Phân quyền `@PreAuthorize` theo role; public: đăng ký, login, refresh, Swagger, health.
  - Chống brute-force bằng Redis (Mục 4.1).
- **DoD:** test: không token → 401; USER gọi API ADMIN → 403; login đúng → token hợp lệ; sai mật khẩu → 401; refresh token cũ không dùng lại được sau khi xoay vòng; sau logout refresh bị từ chối; user bị khóa không đăng nhập được; sai ≥ 5 lần → 429.
- **Kiểm tra:** luồng đăng ký → login → `GET /users/me` bằng token → refresh → logout.

#### BƯỚC 3.6 — Cache Redis
- **Việc cần làm:** bật Spring Cache + Redis cho `GET /users/me` theo `userId`, TTL 10 phút; xóa cache khi cập nhật/khóa/xóa; serializer JSON.
- **DoD:** test chứng minh lần gọi thứ hai không truy vấn DB; cập nhật xóa cache; Redis tạm lỗi thì service vẫn đọc DB và ghi log cảnh báo.
- **Kiểm tra:** integration test với Testcontainers Redis.

#### BƯỚC 3.7 — Outbox + Kafka producer
- **Việc cần làm:** migration `V3__outbox.sql`; khi đăng ký/sửa/xóa user, ghi `outbox_events` **trong cùng transaction**; job `@Scheduled` đọc các bản ghi chưa phát → gửi `user-events` (key = `userId`, envelope 3.5) → đặt `published_at`. Job phải an toàn khi chạy nhiều instance (ví dụ `FOR UPDATE SKIP LOCKED`).
- **DoD:** test với Testcontainers Kafka: đăng ký user → đúng 1 `UserCreatedEvent`; Kafka tạm sập thì sự kiện không mất và được phát lại sau; payload không chứa dữ liệu nhạy cảm.
- **Kiểm tra:** xem message bằng consumer thử hoặc Kafka UI.

#### BƯỚC 3.8 — Dockerfile
- **Việc cần làm:** Dockerfile multi-stage (build Maven → chạy JRE, user không phải root), profile `docker`.
- **DoD:** `docker build` thành công, container chạy và `/actuator/health` = `UP`.

---

### PHASE 4 — NOTIFICATION-SERVICE

#### BƯỚC 4.1 — Khung service & Kafka consumer
- **Việc cần làm:** module `notification-service` (port `8082`); dependency: kafka, data-jpa, postgresql, flyway, mail, thymeleaf, actuator, `common-lib`; migration `V1` tạo `processed_events`, `notification_log`; consumer `user-events` (group id cố định) parse envelope, chỉ xử lý `UserCreatedEvent`, bỏ qua loại khác có log.
- **DoD:** nhận được sự kiện thật từ user-service và ghi log; sự kiện sai định dạng không làm consumer chết (đẩy sang DLT).
- **Kiểm tra:** test với Testcontainers Kafka.

#### BƯỚC 4.2 — Gửi email chào mừng
- **Việc cần làm:** `EmailService` (JavaMailSender), template Thymeleaf `welcome.html` (tiếng Việt), cấu hình SMTP qua biến môi trường (Mailpit khi dev); ghi `notification_log` (`SENT`/`FAILED`).
- **DoD:** đăng ký user → email xuất hiện trong Mailpit với đúng người nhận và tên; nội dung được escape đúng (thử tên có ký tự `<` `&`).
- **Kiểm tra:** mở Mailpit UI `http://localhost:8025`.

#### BƯỚC 4.3 — Idempotent, retry, DLT
- **Việc cần làm:** kiểm tra/ghi `processed_events` trong cùng transaction với `notification_log`; retry có backoff cho lỗi tạm thời (SMTP lỗi); vượt ngưỡng → `user-events.DLT`; lỗi không thể thử lại (dữ liệu sai) → vào DLT ngay.
- **DoD:** gửi trùng cùng `eventId` chỉ tạo 1 email; tắt Mailpit rồi bật lại, email vẫn được gửi sau retry; message lỗi xuất hiện ở DLT.
- **Kiểm tra:** test tích hợp Kafka + SMTP giả (GreenMail/Mailpit).

#### BƯỚC 4.4 — Dockerfile
- **Việc cần làm/DoD:** như BƯỚC 3.8 cho `notification-service`.

---

### PHASE 5 — TÍCH HỢP

#### BƯỚC 5.1 — Docker Compose đầy đủ
- **Việc cần làm:** `docker-compose.yml` chạy hạ tầng + `user-service` + `notification-service`; `depends_on` kèm `healthcheck`; cấu hình qua `.env`.
- **DoD:** máy sạch: `cp .env.example .env && docker compose up -d --build` → mọi container `healthy`.

#### BƯỚC 5.2 — Kiểm thử end-to-end
- **Việc cần làm:** kịch bản tự động (script `docs/e2e.http` hoặc test E2E): đăng ký → thấy email trong Mailpit → login → `GET /users/me` → đổi mật khẩu → login lại bằng mật khẩu mới → logout → refresh bị từ chối.
- **DoD:** kịch bản chạy xanh trên stack docker compose.

---

### PHASE 6 — CI & TÀI LIỆU

#### BƯỚC 6.1 — Quan sát hệ thống mức cơ bản
- **Việc cần làm:** log JSON có `traceId` (user-service truyền `traceId` vào header Kafka, notification-service đọc và đưa vào log); `/actuator/health`, `/actuator/metrics`.
- **DoD:** tìm được log của cùng một lần đăng ký ở cả hai service bằng một `traceId`.

#### BƯỚC 6.2 — CI GitHub Actions
- **Việc cần làm:** workflow build + test (có Testcontainers), JaCoCo, build Docker image, quét phụ thuộc.
- **DoD:** pipeline xanh trên nhánh chính.

#### BƯỚC 6.3 — Tài liệu & rà soát
- **Việc cần làm:** hoàn thiện `README.md` (kiến trúc, cách chạy, cổng, tài khoản ADMIN mẫu), `docs/ARCHITECTURE.md`, `docs/RUNBOOK.md`; rà theo checklist Mục 7.
- **DoD:** người mới clone repo chạy được hệ thống trong ≤ 15 phút; checklist Mục 7 đạt hết.

---

## 6. BẢNG CỔNG

| Thành phần | Cổng |
|-----------|------|
| user-service | 8081 |
| notification-service | 8082 |
| PostgreSQL / Redis / Kafka | 5432 / 6379 / 9092 |
| Mailpit (UI / SMTP) | 8025 / 1025 |

---

## 7. CHECKLIST CHẤT LƯỢNG & BẢO MẬT

- [x] Không có mật khẩu/secret thật trong Git.
- [x] Mật khẩu băm BCrypt; không log mật khẩu, token, email đầy đủ ở mức log nhạy cảm.
- [x] Refresh token lưu dưới dạng hash; xoay vòng; thu hồi khi đổi mật khẩu/logout.
- [x] Mọi endpoint (trừ danh sách public) yêu cầu xác thực và kiểm tra quyền.
- [x] Validate đầu vào; không trả Entity; không lộ `password_hash`.
- [x] Mỗi service có DB riêng; giao tiếp chỉ qua Kafka.
- [x] Sự kiện qua Outbox; consumer idempotent; có DLT.
- [x] Thông báo lỗi đăng nhập không lộ email có tồn tại hay không.
- [x] Test pass trên CI; coverage service layer ≥ 80%.
- [x] Image Docker chạy bằng user không phải root.
- [x] README đủ để người khác chạy lại.

---

## 8. LỖI THƯỜNG GẶP — AI CẦN TRÁNH

| Lỗi | Cách đúng |
|-----|-----------|
| Gọi Kafka trực tiếp trong `@Transactional` | Ghi outbox trong transaction, phát sau |
| Lưu refresh token dạng thô | Lưu hash, xoay vòng khi refresh |
| Trả `Entity` ra API | Dùng DTO + MapStruct |
| `ddl-auto=update` | Dùng Flyway |
| Đưa mật khẩu/hash vào payload sự kiện | Payload chỉ gồm `userId`, `email`, `fullName` |
| Notification gọi ngược user-service để lấy dữ liệu | Tất cả dữ liệu cần thiết có sẵn trong sự kiện |
| JWT secret ghi cứng trong code/`application.yml` | Biến môi trường, tối thiểu 256 bit |
| Test chạy trên DB thật của máy dev | Dùng Testcontainers |
| Làm nhiều bước một lượt rồi mới test | Làm từng bước, test ngay |
| Xóa test lỗi để pipeline xanh | Tìm nguyên nhân gốc, sửa mã nguồn |

---

## 9. CẤU HÌNH & MÔI TRƯỜNG

- Profile: `dev` (chạy local, hạ tầng qua `localhost`), `docker` (qua tên container), `prod` (biến môi trường/secret manager).
- Biến môi trường tối thiểu: `POSTGRES_PASSWORD` (và mật khẩu từng DB), `REDIS_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `JWT_SECRET`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`.
- Mỗi biến mới phải được thêm vào `.env.example` kèm mô tả.

---

## 10. KHI MỞ RỘNG SAU NÀY

| Muốn thêm | Làm thế nào |
|-----------|-------------|
| Tách `auth-service` | Tách `AuthController/AuthService/JwtService/refresh_tokens` ra module mới; user-service phát sự kiện để auth giữ credential; chuyển sang RS256 + JWKS |
| API Gateway + Eureka | Thêm module, route `lb://user-service`, chuyển xác thực JWT lên gateway (service vẫn kiểm tra lại) |
| Product / Order | Sao chép đúng khuôn user-service; xem bản `KE_HOACH_MICROSERVICE.md` (Phase 8–9) |
| Keycloak | Bỏ phần auth trong user-service, cấu hình resource server trỏ tới Keycloak |
| RabbitMQ thay Kafka | Đổi producer/consumer, DLQ thay DLT |

---

## 11. MẪU PROMPT ĐIỀU KHIỂN AI

### 11.1 Prompt khởi đầu (dán một lần)

```
Bạn là kỹ sư Java/Spring Boot senior. Hãy đọc kỹ file KE_HOACH_USER_NOTIFICATION.md
và tuân thủ tuyệt đối Mục 0 (Quy tắc), Mục 3 (Quy ước) và Mục 4 (Đặc tả).
Phạm vi chỉ gồm user-service (đã gộp xác thực) và notification-service.
Mỗi lần tôi chỉ giao MỘT bước, không làm trước các bước sau.
Nếu thiếu thông tin hoặc có mâu thuẫn, hãy hỏi tôi trước khi code.
Hãy xác nhận đã hiểu bằng cách tóm tắt lại trong 5 dòng.
```

### 11.2 Prompt cho từng bước

```
Thực hiện BƯỚC <số bước> trong KE_HOACH_USER_NOTIFICATION.md.
Trước khi code: liệt kê ngắn gọn các file sẽ tạo/sửa và cách kiểm tra.
Sau khi xong, báo cáo theo mẫu:
1. Các file đã tạo/sửa (đường dẫn).
2. Các lệnh đã chạy và kết quả (build, test, chạy thử).
3. Từng mục DoD: ĐẠT / CHƯA ĐẠT (kèm bằng chứng).
4. Điểm chưa chắc chắn hoặc giả định đã dùng.
5. Đề xuất bước tiếp theo.
```

### 11.3 Prompt khi gặp lỗi

```
Bước <số> đang lỗi: <dán log lỗi>.
Hãy tìm nguyên nhân gốc (không đoán), giải thích ngắn gọn, sửa mã nguồn
(không xóa/tắt test), chạy lại test và báo cáo kết quả.
```

### 11.4 Prompt rà soát sau mỗi Phase

```
Rà soát phần đã làm trong PHASE <số> so với Mục 0, Mục 3, Mục 7 và Mục 8 của
KE_HOACH_USER_NOTIFICATION.md. Liệt kê mọi chỗ vi phạm hoặc rủi ro theo mức độ
và đề xuất cách sửa. Chưa sửa gì cho đến khi tôi đồng ý.
```

---

## 12. DEFINITION OF DONE CHUNG

Một bước chỉ hoàn thành khi: (1) mã biên dịch và chạy được; (2) test liên quan đã viết và pass; (3) `mvn test` toàn dự án vẫn xanh; (4) `.env.example`/OpenAPI/tài liệu được cập nhật nếu có thay đổi; (5) đã commit rõ ràng; (6) AI đã nộp báo cáo theo mẫu 11.2.

---

## 13. BẢNG THEO DÕI TIẾN ĐỘ

- [x] 0.1 Chốt thông số
- [x] 0.2 Kiểm tra môi trường
- [x] 0.3 Repo & parent POM
- [x] 1.1 Hạ tầng Docker
- [x] 2.1 common-lib
- [x] 3.1 Khung user-service & DB
- [x] 3.2 Entity/Repo/DTO/Mapper
- [x] 3.3 Service: đăng ký & profile
- [x] 3.4 Controller & lỗi
- [x] 3.5 Xác thực JWT (login/refresh/logout)
- [x] 3.6 Cache Redis
- [x] 3.7 Outbox + Kafka producer
- [x] 3.8 Dockerfile user-service
- [x] 4.1 Khung notification & consumer
- [x] 4.2 Gửi email chào mừng
- [x] 4.3 Idempotent, retry, DLT
- [x] 4.4 Dockerfile notification-service
- [x] 5.1 Compose đầy đủ
- [x] 5.2 E2E
- [x] 6.1 Quan sát cơ bản
- [x] 6.2 CI
- [x] 6.3 Tài liệu & rà soát
