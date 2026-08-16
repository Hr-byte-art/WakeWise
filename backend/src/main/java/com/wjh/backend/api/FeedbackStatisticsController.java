package com.wjh.backend.api;

import com.wjh.backend.domain.feedback.Feedback;
import com.wjh.backend.domain.feedback.FeedbackRepository;
import com.wjh.backend.domain.statistics.StatisticsService;
import com.wjh.backend.infrastructure.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class FeedbackStatisticsController {

    private final FeedbackRepository feedbackRepo;
    private final StatisticsService statisticsService;

    public FeedbackStatisticsController(FeedbackRepository feedbackRepo,
                                         StatisticsService statisticsService) {
        this.feedbackRepo = feedbackRepo;
        this.statisticsService = statisticsService;
    }

    @PostMapping("/feedback")
    public ApiResponse<Void> submitFeedback(@Valid @RequestBody FeedbackRequest req) {
        Feedback fb = new Feedback();
        fb.setUserId(req.userId());
        fb.setDecisionId(req.decisionId());
        fb.setWakeHistoryId(req.wakeHistoryId());
        fb.setRating(req.rating());
        fb.setReasonCode(req.reasonCode());
        fb.setComment(req.comment());
        feedbackRepo.save(fb);
        return ApiResponse.ok(null);
    }

    @GetMapping("/statistics/weekly")
    public ApiResponse<StatisticsService.WeeklyStats> weeklyStats(
            @RequestParam String userId,
            @RequestParam String week) {
        return ApiResponse.ok(statisticsService.getWeeklyStats(userId, week));
    }

    public record FeedbackRequest(
            @NotBlank String userId,
            String decisionId,
            String wakeHistoryId,
            @Min(1) @Max(5) int rating,
            @NotBlank String reasonCode,
            String comment
    ) {}
}