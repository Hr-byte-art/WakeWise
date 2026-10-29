package com.wjh.wakewise.network;

import android.content.Context;

import com.wjh.wakewise.data.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    private static volatile ApiService instance;

    private ApiClient() {
    }

    public static ApiService api(Context context) {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    SessionManager session = new SessionManager(context.getApplicationContext());
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
                    final Context appContext = context.getApplicationContext();
                    OkHttpClient client = new OkHttpClient.Builder()
                            .addInterceptor(chain -> {
                                String token = session.getToken();
                                if (!token.isEmpty()) {
                                    return chain.proceed(chain.request().newBuilder()
                                            .header("Authorization", "Bearer " + token)
                                            .build());
                                }
                                return chain.proceed(chain.request());
                            })
                            .addInterceptor(chain -> {
                                okhttp3.Response response = chain.proceed(chain.request());
                                // 时钟偏差学习是增强链路：任何异常静默，绝不阻塞请求
                                try {
                                    if (response.isSuccessful()) {
                                        ServerClock.learn(appContext, response.header("Date"));
                                    }
                                } catch (Exception ignored) {
                                }
                                return response;
                            })
                            .addInterceptor(logging)
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .build();
                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(session.getBaseUrl())
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    instance = retrofit.create(ApiService.class);
                }
            }
        }
        return instance;
    }
}
