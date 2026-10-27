package com.wjh.wakewise.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * 本地缓存库。version 2 起含晨间简报缓存表；
 * 开发期沿用破坏性迁移：升级清库后已布防闹钟需重新 evaluate + 同步重建。
 */
@Database(entities = {ScheduledAlarm.class, OutboxEvent.class, MorningBriefCache.class},
        version = 2, exportSchema = false)
public abstract class LocalAlarmStore extends RoomDatabase {

    private static volatile LocalAlarmStore instance;

    public abstract ScheduledAlarmDao scheduledAlarmDao();

    public abstract OutboxEventDao outboxEventDao();

    public abstract MorningBriefCacheDao morningBriefCacheDao();

    public static LocalAlarmStore get(Context context) {
        if (instance == null) {
            synchronized (LocalAlarmStore.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    LocalAlarmStore.class, "wakewise.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
