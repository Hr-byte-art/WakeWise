package com.wjh.wakewise.domain.statistics;

import com.wjh.wakewise.domain.decision.DecisionHistory;
import com.wjh.wakewise.domain.decision.DecisionService;
import com.wjh.wakewise.domain.history.WakeHistory;
import com.wjh.wakewise.domain.history.WakeHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private final WakeHistoryRepository wakeHistoryRepository;
    private final DecisionService decisionService;

    public StatisticsService(WakeHistoryRepository wakeHistoryRepository, DecisionService decisionService) {
        this.wakeHistoryRepository = wakeHistoryRepository;
        this.decisionService = decisionService;
    }

    public record WeeklyStatsView(double onTimeRate, int trackedMornings, double averageAdjustmentMinutes,
                                  double recommendationAcceptanceRate, List<String> topRiskFactors) {
    }

    @Transactional(readOnly = true)
    public WeeklyStatsView weekly(String userId) {
        Instant after = Instant.now().minus(7, ChronoUnit.DAYS);

        List<WakeHistory> histories = wakeHistoryRepository.findByUserIdAndScheduledAtAfter(userId, after);
        long success = histories.stream().filter(h -> "SUCCESS".equals(h.getOutcome())).count();
        long late = histories.stream().filter(h -> "LATE".equals(h.getOutcome())).count();
        double onTimeRate = success + late == 0 ? 1.0 : (double) success / (success + late);

        List<DecisionHistory> decisions = decisionService.decisionsAfter(userId, after).stream()
                .filter(d -> d.getStatus() != DecisionHistory.PlanStatus.SUPERSEDED)
                .toList();
        double averageAdjustment = decisions.stream()
                .filter(d -> d.getStatus() == DecisionHistory.PlanStatus.ARMED
                        || d.getStatus() == DecisionHistory.PlanStatus.EXECUTED)
                .mapToInt(DecisionHistory::getDeltaMinutes)
                .average()
                .orElse(0);
        long reverted = decisions.stream()
                .filter(d -> d.getStatus() == DecisionHistory.PlanStatus.REVERTED)
                .count();
        double acceptance = decisions.isEmpty() ? 1.0 : 1.0 - (double) reverted / decisions.size();
        List<String> topRiskFactors = decisions.stream()
                .filter(d -> d.getDeltaMinutes() != 0)
                .collect(Collectors.groupingBy(DecisionHistory::getReasonCode, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        return new WeeklyStatsView(round2(onTimeRate), (int) (success + late), round2(averageAdjustment),
                round2(acceptance), topRiskFactors);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
