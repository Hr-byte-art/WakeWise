package com.wjh.wakewise.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScheduledAlarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ScheduledAlarm alarm);

    @Query("SELECT * FROM scheduled_alarms WHERE planId = :planId")
    ScheduledAlarm findByPlanId(String planId);

    @Query("SELECT * FROM scheduled_alarms")
    List<ScheduledAlarm> getAll();

    @Query("SELECT * FROM scheduled_alarms WHERE alarmId = :alarmId")
    List<ScheduledAlarm> findByAlarmId(String alarmId);

    @Delete
    void delete(ScheduledAlarm alarm);

    @Query("DELETE FROM scheduled_alarms WHERE planId = :planId")
    void deleteByPlanId(String planId);
}
