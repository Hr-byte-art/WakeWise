package com.wjh.backend.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = mapHttpStatus(ex.getErrorCode());
        log.warn("Business error [{}] {} - {}", ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, e -> e.getDefaultMessage() == null ? "invalid" : e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "请求参数校验失败", errors));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.AUTH_TOKEN_EXPIRED, "认证失败"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.AUTH_SCOPE_REQUIRED, "权限不足"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }

    private HttpStatus mapHttpStatus(ErrorCode code) {
        return switch (code) {
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RESOURCE_VERSION_CONFLICT, IDEMPOTENCY_CONFLICT,
                 DECISION_EXPIRED, DECISION_ALREADY_EXECUTED -> HttpStatus.CONFLICT;
            case ALARM_ADJUSTMENT_OUT_OF_RANGE -> HttpStatus.BAD_REQUEST;
            case DECISION_LOW_CONFIDENCE, SAFE_REVERT_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case EXTERNAL_WEATHER_UNAVAILABLE, EXTERNAL_TRAFFIC_UNAVAILABLE,
                 DEVICE_ACK_TIMEOUT -> HttpStatus.SERVICE_UNAVAILABLE;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}