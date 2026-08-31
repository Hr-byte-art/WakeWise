package com.wjh.wakewise.domain.alarm;

import com.wjh.wakewise.common.BusinessException;
import com.wjh.wakewise.common.ErrorCode;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class Recurrence {

    private static final Map<String, DayOfWeek> BY_CODE = Map.of(
            "MO", DayOfWeek.MONDAY,
            "TU", DayOfWeek.TUESDAY,
            "WE", DayOfWeek.WEDNESDAY,
            "TH", DayOfWeek.THURSDAY,
            "FR", DayOfWeek.FRIDAY,
            "SA", DayOfWeek.SATURDAY,
            "SU", DayOfWeek.SUNDAY);

    private Recurrence() {
    }

    public static EnumSet<DayOfWeek> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.allOf(DayOfWeek.class);
        }
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : csv.split(",")) {
            DayOfWeek day = BY_CODE.get(token.trim().toUpperCase());
            if (day == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "无效的周期表达式: " + token);
            }
            days.add(day);
        }
        return days;
    }

    /** 计算 after 之后（不含 after）的下一次发生时刻；days 为空表示每天。 */
    public static Instant nextOccurrence(Set<DayOfWeek> days, LocalTime time, ZoneId zone, Instant after) {
        LocalDate today = LocalDate.ofInstant(after, zone);
        for (int i = 0; i <= 7; i++) {
            LocalDate date = today.plusDays(i);
            if (!days.contains(date.getDayOfWeek())) {
                continue;
            }
            Instant candidate = date.atTime(time).atZone(zone).toInstant();
            if (candidate.isAfter(after)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "无法计算下一次触发时间");
    }
}
