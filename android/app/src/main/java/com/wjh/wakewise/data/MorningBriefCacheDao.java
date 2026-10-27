package com.wjh.wakewise.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface MorningBriefCacheDao {

    @Query("SELECT * FROM morning_brief_cache WHERE id = 1")
    MorningBriefCache get();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MorningBriefCache cache);
}
