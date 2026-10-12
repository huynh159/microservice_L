package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Lấy toàn bộ tin nhắn của một phòng theo thứ tự thời gian tăng dần (từ cũ đến mới)
     */
    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(UUID roomId);

    /**
     * Lấy tin nhắn của một phòng có phân trang
     */
    Page<ChatMessage> findByRoomIdOrderByCreatedAtAsc(UUID roomId, Pageable pageable);

    /**
     * Lấy tin nhắn cuối cùng của một phòng chat để hiển thị dòng tóm tắt (last message preview)
     */
    Optional<ChatMessage> findFirstByRoomIdOrderByCreatedAtDesc(UUID roomId);
}
