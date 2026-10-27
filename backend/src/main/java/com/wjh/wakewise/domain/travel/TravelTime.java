package com.wjh.wakewise.domain.travel;

/**
 * 真实通勤估计结果：available=false 表示不可用（未配 key/缺地址/查询失败），
 * 引擎一律回落用户手填通勤分钟——ETA 是增强链路，"AI 可以失败，闹钟不能"。
 */
public record TravelTime(boolean available, int durationMinutes, double qualityScore) {

    public static TravelTime unavailable() {
        return new TravelTime(false, 0, 0.0);
    }
}
