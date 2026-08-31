package com.wjh.wakewise.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ScheduledAlarm.class, OutboxEvent.class}, version = 1, exportSchema = false)
public abstract class LocalAlarmStore extends RoomDatabase {

    private static volatile LocalAlarmStore instance;

    public abstract ScheduledAlarmDao scheduledAlarmDao();

    public abstract OutboxEventDao outboxEventDao();

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
