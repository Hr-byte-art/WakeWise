package com.wjh.wakewise.common;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 极简线程工具：后台执行 + 回到主线程。 */
public final class Async {

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Async() {
    }

    public static void run(Runnable background) {
        EXEC.execute(background);
    }

    public static void main(Runnable ui) {
        MAIN.post(ui);
    }
}
