package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatRoom;
import com.example.chatservice.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    /**
     * Tìm phòng chat còn đang mở gần nhất của một người dùng
     */
    Optional<ChatRoom> findFirstByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, RoomStatus status);

    /**
     * Lấy toàn bộ lịch sử các phòng chat của một người dùng
     */
    List<ChatRoom> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Dành cho Admin: Lấy danh sách các phòng theo trạng thái (ví dụ OPEN để hỗ trợ)
     */
    List<ChatRoom> findByStatusOrderByCreatedAtDesc(RoomStatus status);

    /**
     * Dành cho Admin: Lấy tất cả các phòng sắp xếp theo thời gian mới nhất
     */
    List<ChatRoom> findAllByOrderByCreatedAtDesc();

    /**
     * Đếm số lượng phòng đang chờ xử lý
     */
    long countByStatus(RoomStatus status);
}
