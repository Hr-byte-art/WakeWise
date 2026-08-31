package com.wjh.wakewise.network;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

/** 提取后端错误响应里的可读提示。非 2xx 时 Retrofit 的 body() 为 null，真实错误在 errorBody()。 */
public final class ApiErrors {

    private ApiErrors() {
    }

    /** 优先取 error.message，取不到（含网络层失败、非 JSON 错误体）时返回 fallback。 */
    public static String describe(@Nullable Response<?> response, String fallback) {
        if (response != null && !response.isSuccessful()) {
            try (ResponseBody raw = response.errorBody()) {
                if (raw != null) {
                    Dtos.ApiEnvelope<?> env = new Gson().fromJson(raw.string(), Dtos.ApiEnvelope.class);
                    if (env != null && env.error != null && env.error.message != null
                            && !env.error.message.isEmpty()) {
                        return env.error.message;
                    }
                }
            } catch (RuntimeException | IOException ignored) {
            }
        }
        return fallback;
    }
}
