package com.example.userservice.repository;

import com.example.userservice.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * ==============================================================================
 * REPOSITORY QUẢN LÝ OUTBOX EVENTS (OUTBOX REPOSITORY) - BƯỚC 3.7
 * ------------------------------------------------------------------------------
 * KIẾN THỨC CỐT LÕI:
 * 1. FOR UPDATE SKIP LOCKED (PostgreSQL):
 *    - Trong môi trường Microservices phân tán, nhiều instance/pod của user-service
 *      sẽ chạy đồng thời cùng một Scheduled Job để đọc Outbox.
 *    - Nếu chỉ dùng `SELECT ... FOR UPDATE`, instance 2 sẽ bị treo (Lock Wait) chờ instance 1 xử lý xong.
 *    - Bằng cách thêm `SKIP LOCKED`, instance 2 sẽ TỰ ĐỘNG BỎ QUA các hàng mà instance 1 đang khóa
 *      và lấy tiếp các hàng chưa bị khóa khác để xử lý.
 *    -> Đạt được cả hai tiêu chí: HOÀN TOÀN KHÔNG XUNG ĐỘT (Concurrency Safe)
 *       và XỬ LÝ SONG SONG ĐẠT HIỆU NĂNG TỐI ĐA!
 * ==============================================================================
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Đọc các sự kiện chưa phát theo thứ tự thời gian có khóa bi quan và bỏ qua các dòng đang bị khóa.
     * Cần chạy trong một Transaction có ghi để giữ lock.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findUnpublishedEventsForUpdate(@Param("limit") int limit);

    /**
     * Tra cứu các sự kiện chưa phát (dùng cho truy vấn thông thường / test)
     */
    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc();

    /**
     * Tra cứu toàn bộ sự kiện của một Aggregate (ví dụ theo userId)
     */
    List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(String aggregateId);
}
