package com.example.chatservice.service;

import com.example.chatservice.dto.response.ChatRoomResponse;
import com.example.chatservice.entity.RoomStatus;

import java.util.List;
import java.util.UUID;

public interface ChatRoomService {

    /**
     * Khách hàng mở khung chat: Tìm phòng chat đang mở gần nhất, nếu chưa có thì tự động tạo phòng mới
     */
    ChatRoomResponse getOrCreateRoomForUser(UUID userId, String email);

    /**
     * Quản trị viên (Admin) bấm "Tiếp nhận hỗ trợ" một phòng chat
     */
    ChatRoomResponse assignAdminToRoom(UUID roomId, UUID adminId);

    /**
     * Cập nhật trạng thái phòng chat (OPEN -> RESOLVED -> CLOSED)
     */
    ChatRoomResponse updateRoomStatus(UUID roomId, RoomStatus status, UUID currentUserId, boolean isAdmin);

    /**
     * Lấy danh sách các phòng chat của người dùng hiện tại
     */
    List<ChatRoomResponse> getMyRooms(UUID userId);

    /**
     * Quản trị viên: Lấy danh sách tất cả các phòng chat trong hệ thống
     */
    List<ChatRoomResponse> getAllRoomsForAdmin();

    /**
     * Lấy chi tiết thông tin một phòng chat theo ID
     */
    ChatRoomResponse getRoomById(UUID roomId);
}
