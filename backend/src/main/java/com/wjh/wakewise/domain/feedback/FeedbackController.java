package com.wjh.wakewise.domain.feedback;

import com.wjh.wakewise.common.ApiResponse;
import com.wjh.wakewise.common.AuthContext;
import com.wjh.wakewise.common.Ids;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;

    public FeedbackController(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public record FeedbackRequest(String decisionId, @Min(1) @Max(5) int rating,
                                  @NotBlank String reasonCode, String comment) {
    }

    @PostMapping
    public ApiResponse<Void> submit(@jakarta.validation.Valid @RequestBody FeedbackRequest request) {
        Feedback feedback = new Feedback();
        feedback.setId(Ids.newId());
        feedback.setUserId(AuthContext.currentUserId());
        feedback.setDecisionId(request.decisionId());
        feedback.setRating(request.rating());
        feedback.setReasonCode(request.reasonCode());
        feedback.setComment(request.comment());
        feedbackRepository.save(feedback);
        return ApiResponse.ok();
    }
}
