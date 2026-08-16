package com.wjh.backend.domain.statistics;

import com.wjh.backend.domain.history.WakeHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Locale;

@Service
public class StatisticsService {

    private final WakeHistoryRepository wakeHistoryRepo;

    public StatisticsService(WakeHistoryRepository wakeHistoryRepo) {
        this.wakeHistoryRepo = wakeHistoryRepo;
    }

    /**
     * 获取用户周度统计
     */
    public WeeklyStats getWeeklyStats(String userId, String weekStr) {
        // 解析 ISO 周，如 "2026-W32"
        int year = Integer.parseInt(weekStr.substring(0, 4));
        int week = Integer.parseInt(weekStr.substring(6));

        WeekFields wf = WeekFields.of(Locale.getDefault());
        ZonedDateTime weekStart = ZonedDateTime.now(ZoneId.of("UTC"))
                .withYear(year)
                .with(wf.weekOfYear(), week)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        ZonedDateTime weekEnd = weekStart.plusDays(7);

        Instant start = weekStart.toInstant();
        Instant end = weekEnd.toInstant();

        long total = wakeHistoryRepo.countTotalByUserAndPeriod(userId, start, end);
        long success = wakeHistoryRepo.countSuccessByUserAndPeriod(userId, start, end);

        double successRate = total > 0 ? (double) success / total : 0;
        double onTimeRate = successRate; // MVP 简化：success = on time

        return new WeeklyStats(
                successRate,
                onTimeRate,
                0.78,  // recommendationAcceptanceRate - MVP 占位
                -8.4,  // averageAdjustmentMinutes - MVP 占位
                24,    // sleepMinutesPreserved - MVP 占位
                5.2,   // preparationPredictionMaeMinutes - MVP 占位
                "[]"   // topRiskFactors - MVP 占位
        );
    }

    public record WeeklyStats(
            double planSuccessRate,
            double onTimeRate,
            double recommendationAcceptanceRate,
            double averageAdjustmentMinutes,
            int sleepMinutesPreserved,
            double preparationPredictionMaeMinutes,
            String topRiskFactors
    ) {}
}