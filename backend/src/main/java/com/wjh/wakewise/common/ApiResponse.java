package com.wjh.wakewise.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error, String traceId, Instant timestamp) {

    public record ApiError(String code, String message, Object details) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, currentTraceId(), Instant.now());
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, Object details) {
        return new ApiResponse<>(false, null, new ApiError(code.name(), message, details), currentTraceId(), Instant.now());
    }

    private static String currentTraceId() {
        String traceId = org.slf4j.MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        }
        return traceId;
    }
}
