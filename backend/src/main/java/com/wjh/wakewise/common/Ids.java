package com.wjh.wakewise.common;

import com.github.f4b6a3.ulid.UlidCreator;

public final class Ids {

    private Ids() {
    }

    public static String newId() {
        return UlidCreator.getUlid().toString();
    }
}
