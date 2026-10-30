# ==============================================================================
# DOCKERFILE CHO CHAT-SERVICE
# Áp dụng kiến trúc Multi-Stage Build chuẩn Enterprise: Tối ưu dung lượng & Bảo mật
# ==============================================================================

# ------------------------------------------------------------------------------
# STAGE 1: BUILD STAGE (Giai đoạn biên dịch và đóng gói)
# ------------------------------------------------------------------------------
# - eclipse-temurin: Bản phân phối OpenJDK chính thức, tiêu chuẩn doanh nghiệp do Eclipse Foundation phát triển.
# - 21-jdk: Chứa đầy đủ bộ công cụ phát triển JDK (Java Development Kit) bao gồm compiler 'javac', 
#   thư viện build, cần thiết để biên dịch mã nguồn Java thành bytecode.
# - jammy: Sử dụng base image là Ubuntu 22.04 LTS (Jammy Jellyfish), ổn định và tương thích cao.
# - AS builder: Đặt tên định danh cho giai đoạn này là "builder" để giai đoạn sau có thể copy kết quả.
FROM eclipse-temurin:21-jdk-jammy AS builder

# Thiết lập thư mục làm việc chính bên trong container cho quá trình build
WORKDIR /workspace

# [TỐI ƯU HÓA DOCKER CACHE LAYER]
# Sao chép Maven Wrapper (bộ cài Maven tự động đi kèm repo) và file pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Xử lý định dạng dòng ký tự giữa Windows và Linux:
# - sed -i 's/\r$//' mvnw: Loại bỏ ký tự xuống dòng CRLF của Windows (\r), chuyển về chuẩn Linux (LF)
#   nhằm ngăn chặn lỗi phổ biến "\r: command not found" khi chạy script trên Linux.
# - chmod +x mvnw: Cấp quyền thực thi (execution permission) cho file script mvnw.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Sao chép pom.xml của tất cả các module trong dự án Maven Multi-module.
# Lý do: Maven cần đọc toàn bộ cây cấu trúc dự án cha - con để phân giải quan hệ phụ thuộc (dependencies).
# Việc COPY các file pom.xml TRƯỚC KHI copy mã nguồn (src) giúp tận dụng triệt để "Docker Layer Cache":
# Nếu mã nguồn thay đổi nhưng file thư viện pom.xml không đổi, Docker sẽ tái sử dụng cache, không cần tải lại dependency.
COPY common-lib/pom.xml common-lib/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY notification-service/pom.xml notification-service/
COPY chat-service/pom.xml chat-service/

# Sao chép mã nguồn của các module cần thiết để phục vụ đóng gói chat-service:
# - common-lib/src: Thư viện nội bộ (chứa DTO dùng chung, Exception, Security Config).
# - chat-service/src: Nghiệp vụ phòng chat và tin nhắn WebSocket.
# (Các service khác như user-service, notification-service không cần copy vào đây để đảm bảo tính cô lập).
COPY common-lib/src common-lib/src
COPY chat-service/src chat-service/src

# Tiến hành biên dịch và đóng gói thành file JAR:
# - ./mvnw clean package: Dọn dẹp thư mục target cũ và đóng gói ứng dụng.
# - -DskipTests: Bỏ qua chạy kiểm thử tự động (Unit Test / Integration Test) trong lúc đóng gói Docker Image 
#   (các test này thông thường đã được chạy ở pipeline CI/CD trước đó).
# - -pl common-lib,chat-service (--projects): Chỉ định Maven CHỈ build 2 module này.
# - -am (--also-make): Yêu cầu Maven tự động phân tích và build các module phụ thuộc trước (ở đây là common-lib).
RUN ./mvnw clean package -DskipTests -pl common-lib,chat-service -am


# ------------------------------------------------------------------------------
# STAGE 2: RUNTIME STAGE (Giai đoạn chạy ứng dụng thực tế)
# ------------------------------------------------------------------------------
# - 21-jre: Chỉ sử dụng JRE (Java Runtime Environment), KHÔNG chứa compiler 'javac' và công cụ build thừa thãi.
#   Lợi ích: Giảm kích thước image từ ~800MB xuống chỉ còn ~200MB, đồng thời giảm thiểu bề mặt tấn công bảo mật 
#   (kẻ tấn công không thể compile mã độc nếu đột nhập được vào container).
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# [THỰC HÀNH BẢO MẬT OWASP / DOCKER BEST PRACTICE: NON-ROOT USER]
# Mặc định Docker chạy với tài khoản root (UID 0). Nếu ứng dụng bị lỗ hổng RCE (Remote Code Execution),
# kẻ tấn công sẽ có quyền root trên container và có nguy cơ thoát vùng chứa (Container Breakout) chiếm máy chủ.
# - groupadd -r appgroup: Tạo nhóm người dùng hệ thống 'appgroup'.
# - useradd -r -g appgroup -u 1001 appuser: Tạo người dùng hệ thống 'appuser' (UID 1001, non-root).
# - USER appuser: Chuyển toàn bộ quyền thực thi từ điểm này sang user 'appuser'.
RUN groupadd -r appgroup && useradd -r -g appgroup -u 1001 appuser
USER appuser

# Lấy duy nhất file JAR thành phẩm từ Stage 1 ("builder") sang thư mục /app/app.jar của Stage 2.
# Toàn bộ mã nguồn nguồn gốc (.java), Maven cache, công cụ build ở Stage 1 hoàn toàn bị hủy bỏ.
COPY --from=builder /workspace/chat-service/target/chat-service-1.0.0-SNAPSHOT.jar app.jar

# Khai báo cổng mà container sẽ lắng nghe:
# - Cổng 8083: Phục vụ HTTP REST API quản lý phòng chat và giao thức WebSocket (STOMP) truyền nhận tin nhắn.
EXPOSE 8083

# Thiết lập các biến môi trường (Environment Variables) cho JVM và Spring Boot:
# - JAVA_OPTS:
#   + -Xms256m: Cấp phát bộ nhớ Heap khởi điểm cho JVM là 256MB.
#   + -Xmx512m: Giới hạn bộ nhớ Heap tối đa là 512MB (tránh việc Java ngốn hết RAM máy host gây sập container bởi OOM Killer).
#   + -XX:+UseG1GC: Kích hoạt bộ thu gom rác G1 (Garbage-First Garbage Collector), tối ưu độ trễ cho ứng dụng microservice.
# - SPRING_PROFILES_ACTIVE: Kích hoạt cấu hình môi trường 'docker' (nạp file application-docker.yml).
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE="docker"

# Lệnh khởi chạy ứng dụng khi container bắt đầu chạy:
# - Sử dụng cú pháp ["sh", "-c", "..."] để shell Linux có thể đọc và nội suy (expand) biến môi trường $JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
