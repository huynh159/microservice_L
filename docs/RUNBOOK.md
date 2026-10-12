# 📖 Operational Runbook & Disaster Recovery — Microservice Platform

Tài liệu hướng dẫn vận hành, giám sát và xử lý sự cố thực tế (SOP - Standard Operating Procedures) cho hệ thống **Microservice Platform**.

---

## 1. Kiểm Tra Sức Khỏe & Chẩn Đoán Nhanh (Quick Health Diagnostics)

### 1.1. Kiểm tra trạng thái toàn bộ Container
```bash
docker compose ps
```
*Tất cả 7 container phải có trạng thái `healthy` hoặc `running`.*

### 1.2. Kiểm tra Actuator Health Check
```bash
# Kiểm tra user-service
curl -s http://localhost:8081/actuator/health | jq .

# Kiểm tra notification-service
curl -s http://localhost:8082/actuator/health | jq .

# Kiểm tra chat-service
curl -s http://localhost:8083/actuator/health | jq .
```
Nếu trạng thái trả về `"status": "DOWN"`, kiểm tra chi tiết trường `components` (db, redis, mail, diskSpace) để xác định dịch vụ phụ thuộc nào bị gián đoạn.

### 1.3. Kiểm tra Distributed Tracing trong Log
Để theo dõi toàn bộ hành trình của một request từ API tới khi gửi email:
```bash
# Tìm kiếm theo Trace ID trên user-service
docker logs microservice_user_service 2>&1 | grep "trace-id-can-tim"

# Tìm kiếm theo Trace ID trên notification-service
docker logs microservice_notification_service 2>&1 | grep "trace-id-can-tim"
```

---

## 2. Quy Trình Vận Hành Sự Cố (SOPs - Standard Operating Procedures)

### SOP 1: Kafka Broker Gặp Sự Cố Hoặc Bị Treo (Kafka Broker Outage)
* **Hiện tượng**: `user-service` hoặc `notification-service` xuất hiện log cảnh báo: `Connection to node -1 (localhost:9092) could not be established. Broker may not be available.`
* **Cơ chế tự bảo vệ**:
  - Nhờ có **Transactional Outbox Pattern**, `user-service` **KHÔNG** bị gián đoạn nghiệp vụ đăng ký/cập nhật người dùng. Dữ liệu sự kiện vẫn được lưu trữ an toàn trong bảng `outbox_events` với `published_at = NULL`.
  - Không có sự kiện nào bị mất dữ liệu!
* **Biện pháp xử lý**:
  1. Kiểm tra trạng thái container Kafka:
     ```bash
     docker logs microservice_kafka --tail 100
     ```
  2. Khởi động lại Kafka:
     ```bash
     docker compose restart kafka
     ```
  3. Sau khi Kafka phục hồi, `OutboxEventProcessor` sẽ tự động quét các bản ghi chưa phát và tiếp tục gửi bù lên Kafka theo đúng thứ tự thời gian.

---

### SOP 2: Thông Báo Xuất Hiện Ở Dead Letter Topic (DLT Alert)
* **Hiện tượng**: Có tin nhắn bị đẩy vào topic `user-events.DLT`.
* **Nguyên nhân**: Bản tin JSON sai định dạng, thiếu trường bắt buộc hoặc cấu trúc payload không tương thích với consumer.
* **Biện pháp xử lý**:
  1. Xem nội dung tin nhắn trong DLT:
     ```bash
     docker exec -it microservice_kafka /opt/kafka/bin/kafka-console-consumer.sh \
       --bootstrap-server localhost:9092 \
       --topic user-events.DLT \
       --from-beginning \
       --property print.headers=true
     ```
  2. Xác định header `kafka_dlt-exception-message` và `kafka_dlt-exception-fqcn` để nắm bắt nguyên nhân lỗi ném ra.
  3. Nếu do lỗi format từ phía nguồn phát (user-service), sửa lỗi ứng dụng và phát hành bản vá.
  4. Sau khi sửa lỗi, phát lại bản tin (Replay) sang topic chính `user-events` nếu cần thiết.

---

### SOP 3: Redis Cache Gặp Sự Cố Hoặc Ngắt Kết Nối (Redis Cache Failure)
* **Hiện tượng**: Log `user-service` có cảnh báo `RedisConnectionFailureException`.
* **Cơ chế tự bảo vệ**:
  - Các service được bọc `try-catch` an toàn khi truy xuất cache hoặc fallback đọc trực tiếp từ PostgreSQL Database.
  - Hệ thống vẫn phục vụ bình thường nhưng độ trễ đọc hồ sơ có thể tăng nhẹ.
* **Biện pháp xử lý**:
  1. Kiểm tra container Redis:
     ```bash
     docker logs microservice_redis --tail 50
     docker exec -it microservice_redis redis-cli -a redis_dev_pass ping
     ```
  2. Khởi động lại Redis:
     ```bash
     docker compose restart redis
     ```
  3. Xóa cache cũ nếu phát hiện dữ liệu không đồng bộ:
     ```bash
     docker exec -it microservice_redis redis-cli -a redis_dev_pass FLUSHDB
     ```

---

### SOP 4: Máy Chủ Mail (Mailpit / SMTP) Gặp Sự Cố (SMTP Down)
* **Hiện tượng**: `notification-service` log lỗi `MailException` hoặc `MailSendException`.
* **Cơ chế tự bảo vệ**:
  - `UserEventsConsumer` cấu hình retry tự động với backoff.
  - Bản ghi trong `consumed_events` chỉ được commit khi gửi mail thành công; nếu lỗi, transaction rollback để cho phép thử lại.
* **Biện pháp xử lý**:
  1. Kiểm tra trạng thái Mailpit:
     ```bash
     docker logs microservice_mailpit --tail 50
     ```
  2. Truy cập web UI Mailpit kiểm tra hòm thư: [http://localhost:8025](http://localhost:8025).
  3. Khởi động lại Mailpit:
     ```bash
     docker compose restart mailpit
     ```

---

### SOP 5: Lỗi Migration Cơ Sở Dữ Liệu Flyway (Flyway Checksum Mismatch)
* **Hiện tượng**: Service không khởi động được, ném ra ngoại lệ `FlywayValidateException: Migration checksum mismatch for migration version X`.
* **Nguyên nhân**: File migration SQL đã bị sửa đổi sau khi đã được thực thi trên môi trường DB.
* **Biện pháp xử lý**:
  1. **Trong môi trường DEV**:
     Nếu cần reset lại dữ liệu sạch ban đầu:
     ```bash
     docker compose down -v
     docker compose up -d
     ```
  2. **Trong môi trường STAGING / PROD**:
     - Tuyệt đối không xóa database.
     - Tạo một file migration mới tăng dần số phiên bản (ví dụ `V4__fix_indexes.sql`) để sửa chữa schema.
     - Hoặc sửa trực tiếp bảng `flyway_schema_history` bằng câu lệnh `flyway repair` thông qua Maven wrapper:
       ```bash
       ./mvnw flyway:repair -pl user-service
       ```

---

## 3. Các Lệnh Vận Hành Thông Dụng (Useful CLI Cheatsheet)

| Nhu cầu vận hành | Câu lệnh thực hiện |
| :--- | :--- |
| **Xem log trực tiếp một service** | `docker logs -f microservice_user_service` |
| **Xem log kèm timestamp** | `docker logs -f -t microservice_notification_service` |
| **Kiểm tra danh sách Topic Kafka** | `docker exec -it microservice_kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` |
| **Xem chi tiết Topic user-events** | `docker exec -it microservice_kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic user-events` |
| **Truy cập CLI PostgreSQL user_db** | `docker exec -it microservice_postgres psql -U user_service_db_user -d user_db` |
| **Truy cập CLI Redis** | `docker exec -it microservice_redis redis-cli -a redis_dev_pass` |
| **Khởi động lại toàn bộ hệ thống** | `docker compose restart` |
| **Xóa sạch dữ liệu và khởi động lại** | `docker compose down -v && docker compose up -d --build` |
