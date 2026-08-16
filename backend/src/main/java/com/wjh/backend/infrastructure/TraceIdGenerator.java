package com.wjh.backend.infrastructure;

import com.github.f4b6a3.ulid.UlidCreator;

public final class TraceIdGenerator {
    private TraceIdGenerator() {}

    public static String next() {
        return UlidCreator.getUlid().toString();
    }
}