package com.wjh.wakewise.domain.statistics;

import com.wjh.wakewise.common.ApiResponse;
import com.wjh.wakewise.common.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/weekly")
    public ApiResponse<StatisticsService.WeeklyStatsView> weekly() {
        return ApiResponse.ok(statisticsService.weekly(AuthContext.currentUserId()));
    }
}
