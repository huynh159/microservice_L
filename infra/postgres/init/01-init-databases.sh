#!/bin/sh
# Tạo database + user riêng cho từng service.
# Mật khẩu lấy từ biến môi trường (truyền qua docker-compose từ file .env), không hard-code.
# Script chỉ chạy MỘT lần khi volume postgres_data còn trống.
set -e

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" --dbname postgres \
  -v user_db="$USER_DB_NAME" -v user_user="$USER_DB_USER" -v user_pass="$USER_DB_PASSWORD" \
  -v noti_db="$NOTIFICATION_DB_NAME" -v noti_user="$NOTIFICATION_DB_USER" -v noti_pass="$NOTIFICATION_DB_PASSWORD" <<'EOSQL'
CREATE USER :"user_user" WITH PASSWORD :'user_pass';
CREATE DATABASE :"user_db" OWNER :"user_user";
REVOKE ALL ON DATABASE :"user_db" FROM PUBLIC;

CREATE USER :"noti_user" WITH PASSWORD :'noti_pass';
CREATE DATABASE :"noti_db" OWNER :"noti_user";
REVOKE ALL ON DATABASE :"noti_db" FROM PUBLIC;
EOSQL
