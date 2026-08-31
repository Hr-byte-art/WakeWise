package com.wjh.wakewise.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface OutboxEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(OutboxEvent event);

    @Query("SELECT * FROM outbox_events")
    List<OutboxEvent> getAll();

    @Query("DELETE FROM outbox_events WHERE eventId = :eventId")
    void deleteByEventId(String eventId);
}
