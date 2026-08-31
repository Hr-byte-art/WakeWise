package com.wjh.wakewise.data;

import android.content.Context;
import android.content.SharedPreferences;

/** 会话与端点配置。默认 baseUrl 指向模拟器宿主机（10.0.2.2），真机请改为局域网地址。 */
public class SessionManager {

    private static final String PREFS = "wakewise_session";
    private static final String DEFAULT_BASE_URL = "http://10.0.2.2:8080/api/v1/";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasToken() {
        return !prefs.getString("token", "").isEmpty();
    }

    public String getToken() {
        return prefs.getString("token", "");
    }

    public void saveAuth(String token, String userId, String email) {
        prefs.edit().putString("token", token)
                .putString("userId", userId)
                .putString("email", email)
                .apply();
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    public String getBaseUrl() {
        return prefs.getString("baseUrl", DEFAULT_BASE_URL);
    }

    public void setBaseUrl(String url) {
        prefs.edit().putString("baseUrl", url).apply();
    }
}
