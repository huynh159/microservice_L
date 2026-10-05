# ==============================================================================
# DOCKERFILE CHO USER-SERVICE (Multi-Stage Build chuẩn tối ưu kích thước)
# ==============================================================================
# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Sao chép Maven wrapper và các file pom.xml để tận dụng Docker cache layer
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common-lib/pom.xml common-lib/
COPY user-service/pom.xml user-service/
COPY notification-service/pom.xml notification-service/

# Tải trước các dependencies (Offline cache)
RUN ./mvnw dependency:go-offline -B -pl common-lib,user-service

# Sao chép mã nguồn và thực hiện build
COPY common-lib/src common-lib/src
COPY user-service/src user-service/src
RUN ./mvnw clean package -DskipTests -pl common-lib,user-service

# ------------------------------------------------------------------------------
# Stage 2: Runtime (Chỉ giữ JRE nhẹ nhàng, tối ưu bảo mật và kích thước Image)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Tạo non-root user để chạy ứng dụng (Best Practice bảo mật)
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

# Sao chép file jar đã build từ Stage 1
COPY --from=builder /workspace/user-service/target/user-service-1.0.0-SNAPSHOT.jar app.jar

# Mở cổng 8081
EXPOSE 8081

# Cấu hình biến môi trường JVM
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE="docker"

# Lệnh khởi chạy ứng dụng
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
