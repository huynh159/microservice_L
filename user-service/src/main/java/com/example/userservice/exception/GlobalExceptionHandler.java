package com.example.userservice.exception;

import com.example.common.dto.response.ErrorDetail;
import com.example.common.dto.response.ErrorResponse;
import com.example.common.exception.BadRequestException;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictException;
import com.example.common.exception.ForbiddenException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ==============================================================================
 * KIẾN THỨC CỐT LÕI VỀ XỬ LÝ LỖI TOÀN CỤC (GLOBAL EXCEPTION HANDLING):
 * ------------------------------------------------------------------------------
 * 1. @RestControllerAdvice:
 *    - Là sự kết hợp của @ControllerAdvice và @ResponseBody.
 *    - Áp dụng nguyên lý AOP (Aspect-Oriented Programming): Nó hoạt động như một
 *      "lưới đánh cá" bọc quanh toàn bộ các @RestController trong ứng dụng.
 *    - Bất kể ngoại lệ xảy ra ở Controller, Service, Mapper hay Repository, nếu
 *      không được try-catch cục bộ, ngoại lệ sẽ nổi lên (bubble up) và rơi vào đây.
 *
 * 2. Lợi ích vượt trội trong kiến trúc Microservices:
 *    - Chuẩn hóa đầu ra (Consistent Error Format): Mọi lỗi đều trả về cùng một cấu trúc
 *      JSON ErrorResponse định nghĩa trong common-lib (timestamp, status, code, message, path, traceId).
 *    - Giấu nhẹm thông tin nhạy cảm: Không bao giờ trả stacktrace của máy chủ cho client
 *      ở môi trường thực tế (tránh lộ cấu trúc thư mục, phiên bản thư viện, lỗ hổng bảo mật).
 *    - Phân biệt rõ ràng lỗi Client (HTTP 4xx) và lỗi Hệ thống (HTTP 5xx).
 *
 * 3. Trace ID (Mã truy vết phân tán):
 *    - Mỗi lỗi trả về đều kèm một traceId ngẫu nhiên (hoặc lấy từ Gateway qua header X-Trace-Id).
 *    - Khi người dùng báo lỗi "Hệ thống bị lỗi kèm traceId: xyz", kỹ sư vận hành chỉ cần
 *      tìm kiếm xyz trên hệ thống Log (ELK / Loki) là tìm ra chính xác log lỗi mà không cần mò mẫm.
 * ==============================================================================
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Xử lý lỗi không tìm thấy tài nguyên (HTTP 404 NOT FOUND)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        log.warn("ResourceNotFoundException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request, null);
    }

    /**
     * Xử lý lỗi xung đột dữ liệu - ví dụ email đã tồn tại (HTTP 409 CONFLICT)
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(
            ConflictException ex,
            HttpServletRequest request
    ) {
        log.warn("ConflictException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request, null);
    }

    /**
     * Xử lý lỗi dữ liệu không hợp lệ do logic nghiệp vụ (HTTP 400 BAD REQUEST)
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(
            BadRequestException ex,
            HttpServletRequest request
    ) {
        log.warn("BadRequestException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request, null);
    }

    /**
     * Xử lý lỗi chưa xác thực / token không hợp lệ (HTTP 401 UNAUTHORIZED)
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            UnauthorizedException ex,
            HttpServletRequest request
    ) {
        log.warn("UnauthorizedException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage(), request, null);
    }

    /**
     * Xử lý lỗi không có quyền truy cập tài nguyên (HTTP 403 FORBIDDEN)
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(
            ForbiddenException ex,
            HttpServletRequest request
    ) {
        log.warn("ForbiddenException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage(), request, null);
    }

    /**
     * Xử lý ngoại lệ nghiệp vụ chung kế thừa từ BusinessException
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {
        log.warn("BusinessException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        HttpStatus status = HttpStatus.resolve(ex.getStatus());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return buildErrorResponse(status, ex.getCode(), ex.getMessage(), request, null);
    }

    /**
     * Xử lý lỗi xác thực Bean Validation (@Valid trên RequestBody)
     * Ví dụ: email không đúng định dạng, mật khẩu < 8 ký tự, họ tên bị để trống
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        log.warn("MethodArgumentNotValidException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());

        List<ErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(new ErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()));
        }

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Dữ liệu gửi lên không đáp ứng quy chuẩn",
                request,
                details
        );
    }

    /**
     * Xử lý lỗi xác thực ràng buộc tham số (@PathVariable, @RequestParam)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        log.warn("ConstraintViolationException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());

        List<ErrorDetail> details = ex.getConstraintViolations().stream()
                .map(v -> new ErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "CONSTRAINT_VIOLATION",
                "Tham số truy vấn không hợp lệ",
                request,
                details
        );
    }

    /**
     * Xử lý lỗi sai định dạng JSON gửi lên (ví dụ: thiếu dấu ngoặc kép, sai kiểu dữ liệu Enum)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.warn("HttpMessageNotReadableException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON_REQUEST",
                "Định dạng JSON gửi lên không đọc được hoặc sai kiểu dữ liệu",
                request,
                null
        );
    }

    /**
     * Xử lý lỗi sai kiểu dữ liệu của biến trên URL (ví dụ: truyền 'abc' vào tham số UUID)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        log.warn("MethodArgumentTypeMismatchException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        String message = String.format("Tham số '%s' có giá trị không đúng kiểu yêu cầu (%s)",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "không xác định");

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", message, request, null);
    }

    /**
     * Xử lý khi gọi endpoint không tồn tại (Spring Boot 3.x)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        log.warn("NoResourceFoundException tại URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "ENDPOINT_NOT_FOUND",
                "Không tìm thấy tài nguyên hoặc endpoint yêu cầu",
                request,
                null
        );
    }

    /**
     * Bắt mọi ngoại lệ không mong muốn còn lại (Catch-All) - HTTP 500 INTERNAL SERVER ERROR
     * KIẾN THỨC BẢO MẬT: Tuyệt đối không trả nguyên văn lỗi hệ thống ra cho Client!
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllUncaughtException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("LỖI HỆ THỐNG CHƯA ĐƯỢC XỬ LÝ tại URI [{}]", request.getRequestURI(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Hệ thống gặp sự cố không mong muốn. Vui lòng liên hệ quản trị viên.",
                request,
                null
        );
    }

    // =========================================================================
    // HÀM TIỆN ÍCH DỰNG ĐỐI TƯỢNG ERROR RESPONSE CHUẨN
    // =========================================================================
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request,
            List<ErrorDetail> details
    ) {
        String traceId = resolveTraceId(request);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .traceId(traceId)
                .details(details)
                .build();

        return ResponseEntity.status(status).body(response);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("X-Request-Id");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }
}
