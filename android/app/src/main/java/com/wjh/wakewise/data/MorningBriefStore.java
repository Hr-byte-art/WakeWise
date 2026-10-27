package com.wjh.wakewise.data;

import android.content.Context;

import com.google.gson.Gson;
import com.wjh.wakewise.network.Dtos.MorningContextDto;

/**
 * 晨间简报缓存读写：首页与响铃页成功拉取后写入，响铃页网络失败时兜底读取。
 * 缓存是增强链路，任何异常静默降级，不影响主流程。调用方需保证在后台线程。
 */
public final class MorningBriefStore {

    private static final Gson GSON = new Gson();

    private MorningBriefStore() {
    }

    /** 读最近一次缓存；无缓存或数据损坏返回 null。 */
    public static MorningContextDto read(Context context) {
        try {
            MorningBriefCache cache = LocalAlarmStore.get(context).morningBriefCacheDao().get();
            return cache == null || cache.payloadJson == null
                    ? null : GSON.fromJson(cache.payloadJson, MorningContextDto.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 覆盖写缓存。 */
    public static void write(Context context, MorningContextDto brief) {
        try {
            MorningBriefCache cache = new MorningBriefCache();
            cache.payloadJson = GSON.toJson(brief);
            cache.cachedAtMillis = System.currentTimeMillis();
            LocalAlarmStore.get(context).morningBriefCacheDao().upsert(cache);
        } catch (Exception ignored) {
            // 缓存失败不影响主流程
        }
    }
}
