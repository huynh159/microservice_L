-- Migration V2: Create refresh_tokens table for Step 3.5 (JWT Authentication & Refresh Token Rotation)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    replaced_by VARCHAR(255)
);

-- Index tra cứu nhanh refresh token theo chuỗi băm (token_hash)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_hash ON refresh_tokens (token_hash);

-- Index tra cứu toàn bộ token của một người dùng (hỗ trợ thu hồi hàng loạt khi logout/đổi mật khẩu)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
