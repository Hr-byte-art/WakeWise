package com.wjh.wakewise.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
    AUTH_FAILED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_VERSION_CONFLICT(HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    ALARM_ADJUSTMENT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST),
    DECISION_NOT_CONFIRMABLE(HttpStatus.CONFLICT),
    DECISION_ALREADY_EXECUTED(HttpStatus.CONFLICT),
    EXTERNAL_WEATHER_UNAVAILABLE(HttpStatus.OK),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
