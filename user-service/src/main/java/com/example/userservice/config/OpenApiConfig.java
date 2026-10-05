package com.example.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ==============================================================================
 * KIẾN THỨC CẤU HÌNH TÀI LIỆU API (OPENAPI / SWAGGER UI):
 * ------------------------------------------------------------------------------
 * 1. OpenAPI 3.0: Chuẩn tài liệu hóa RESTful API phổ biến nhất hiện nay.
 * 2. springdoc-openapi: Thư viện tự động quét mã nguồn (Controllers, DTOs, Annotations)
 *    để sinh ra tài liệu JSON chuẩn tại endpoint: /v3/api-docs
 *    và giao diện trực quan Swagger UI tại endpoint: /swagger-ui.html
 * 3. SecurityScheme (Bearer JWT): Cấu hình sẵn nút "Authorize" trên Swagger UI
 *    để lập trình viên/tester có thể dán JWT Token vào và gọi thử các API được bảo vệ.
 * ==============================================================================
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8081}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .description("Tài liệu API Dịch vụ Quản lý Người dùng & Xác thực trong hệ thống Microservices")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Dev Team")
                                .email("dev@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://springdoc.org")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local Development Server")
                ))
                // Cấu hình cơ chế bảo mật Bearer JWT trên Swagger UI
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập Access Token JWT (không cần gõ tiền tố 'Bearer ')")));
    }
}
