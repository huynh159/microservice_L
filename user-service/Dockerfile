# ==============================================================================
# DOCKERFILE CHO USER-SERVICE (Multi-Stage Build chuẩn tối ưu kích thước) - BƯỚC 3.8
# ==============================================================================
# Stage 1: Build Stage với JDK 21
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Sao chép Maven wrapper và các file pom.xml để tận dụng Docker cache layer
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY common-lib/pom.xml common-lib/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY notification-service/pom.xml notification-service/
COPY chat-service/pom.xml chat-service/

# Sao chép mã nguồn các module cần thiết
COPY common-lib/src common-lib/src
COPY user-service/src user-service/src

# Thực hiện build jar cho user-service và common-lib (bỏ qua tests trong docker build)
RUN ./mvnw clean package -DskipTests -pl common-lib,user-service -am

# ------------------------------------------------------------------------------
# Stage 2: Runtime Stage (Chỉ giữ JRE nhẹ nhàng, tối ưu bảo mật và kích thước Image)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Tạo non-root user để chạy ứng dụng (Best Practice bảo mật OWASP/Docker)
RUN groupadd -r appgroup && useradd -r -g appgroup -u 1001 appuser
USER appuser

# Sao chép file jar đã build từ Stage 1
COPY --from=builder /workspace/user-service/target/user-service-1.0.0-SNAPSHOT.jar app.jar

# Mở cổng 8081
EXPOSE 8081

# Cấu hình biến môi trường JVM & Profile docker
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE="docker"

# Lệnh khởi chạy ứng dụng
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
