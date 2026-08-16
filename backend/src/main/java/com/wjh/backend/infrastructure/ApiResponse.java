package com.wjh.backend.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String traceId,
        Instant timestamp,
        ErrorBody error
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, TraceIdGenerator.next(), Instant.now(), null);
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, traceId, Instant.now(), null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, Object details) {
        return new ApiResponse<>(false, null, TraceIdGenerator.next(), Instant.now(),
                new ErrorBody(code.name(), message, details));
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return error(code, message, null);
    }

    public record ErrorBody(String code, String message, Object details) {}
}