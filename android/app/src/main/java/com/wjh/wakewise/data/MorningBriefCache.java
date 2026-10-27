package com.wjh.wakewise.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 最近一次成功拉取的晨间简报（单行表，id 恒为 1），响铃页离线兜底。 */
@Entity(tableName = "morning_brief_cache")
public class MorningBriefCache {

    @PrimaryKey
    public int id = 1;

    /** MorningContextDto 的 JSON 序列化。 */
    public String payloadJson;

    public long cachedAtMillis;
}
