-- ==============================================================================
-- MIGRATION V1: KHỞI TẠO BẢNG CHAT ROOMS VÀ CHAT MESSAGES
-- ------------------------------------------------------------------------------
-- 1. chat_rooms: Quản lý phiên hội thoại giữa Người dùng (User) và Quản trị viên (Admin).
--    - user_id: Lưu ID của khách hàng tạo phòng.
--    - user_email: Lưu email lấy từ JWT claim (giúp Admin xem danh sách mà không cần gọi user-service).
--    - admin_id: ID của admin tiếp nhận (NULL = chưa có ai hỗ trợ).
--    - status: OPEN (đang chat), RESOLVED (đã hỗ trợ xong), CLOSED (đã đóng).
-- 2. chat_messages: Lưu toàn bộ lịch sử tin nhắn của từng phòng chat.
-- ==============================================================================

CREATE TABLE chat_rooms (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    admin_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);

-- Chỉ mục (Indexes) tối ưu hóa truy vấn tìm kiếm nhanh
CREATE INDEX idx_chat_rooms_user_id ON chat_rooms(user_id);
CREATE INDEX idx_chat_rooms_status ON chat_rooms(status);
CREATE INDEX idx_chat_messages_room_id ON chat_messages(room_id);
CREATE INDEX idx_chat_messages_created_at ON chat_messages(created_at ASC);
