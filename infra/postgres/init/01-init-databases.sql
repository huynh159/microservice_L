-- Create DB and User for user-service
CREATE USER user_service_db_user WITH PASSWORD 'user_service_db_pass';
CREATE DATABASE user_db OWNER user_service_db_user;
GRANT ALL PRIVILEGES ON DATABASE user_db TO user_service_db_user;

-- Create DB and User for notification-service
CREATE USER notification_service_db_user WITH PASSWORD 'notification_service_db_pass';
CREATE DATABASE notification_db OWNER notification_service_db_user;
GRANT ALL PRIVILEGES ON DATABASE notification_db TO notification_service_db_user;
