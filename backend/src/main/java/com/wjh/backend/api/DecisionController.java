package com.wjh.backend.api;

import com.wjh.backend.agent.PlanOrchestrator;
import com.wjh.backend.domain.decision.*;
import com.wjh.backend.infrastructure.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DecisionController {

    private final DecisionService decisionService;
    private final DevicePlanService devicePlanService;
    private final PlanOrchestrator planOrchestrator;
    private final DecisionHistoryRepository decisionRepo;

    public DecisionController(DecisionService decisionService,
                              DevicePlanService devicePlanService,
                              PlanOrchestrator planOrchestrator,
                              DecisionHistoryRepository decisionRepo) {
        this.decisionService = decisionService;
        this.devicePlanService = devicePlanService;
        this.planOrchestrator = planOrchestrator;
        this.decisionRepo = decisionRepo;
    }

    // --- 闹钟预览 ---
    @PostMapping("/alarms/{alarmId}/preview")
    public ApiResponse<PlanPreviewResponse> preview(@PathVariable String alarmId,
                                                     @Valid @RequestBody PreviewRequest req) {
        WakePlan plan = planOrchestrator.planForAlarm(alarmId, req.geoHash());
        return ApiResponse.ok(new PlanPreviewResponse(
                plan.baselineWakeAt(), plan.recommendedWakeAt(), plan.deltaMinutes(),
                plan.lateProbability(), plan.confidence(), plan.expiresAt()
        ));
    }

    // --- 决策评估 ---
    @PostMapping("/decisions/evaluate")
    public ApiResponse<EvaluateResponse> evaluate(@Valid @RequestBody EvaluateRequest req) {
        WakePlan plan = decisionService.evaluate(req.alarmId(), req.targetDate(), req.geoHash());
        return ApiResponse.ok(new EvaluateResponse(plan.planId(), "QUEUED"));
    }

    // --- 查询计划 ---
    @GetMapping("/decisions/{planId}")
    public ApiResponse<PlanDetailResponse> getPlan(@PathVariable String planId) {
        DecisionHistory dh = decisionService.getPlan(planId);
        return ApiResponse.ok(toPlanDetail(dh));
    }

    // --- 确认计划 ---
    @PostMapping("/decisions/{planId}/confirm")
    public ApiResponse<PlanDetailResponse> confirm(@PathVariable String planId,
                                                    @Valid @RequestBody ConfirmRequest req) {
        DecisionHistory dh = decisionService.confirm(planId, req.version());
        return ApiResponse.ok(toPlanDetail(dh));
    }

    // --- 撤销计划 ---
    @PostMapping("/decisions/{planId}/revert")
    public ApiResponse<PlanDetailResponse> revert(@PathVariable String planId,
                                                   @Valid @RequestBody RevertRequest req) {
        DecisionHistory dh = decisionService.revert(planId, req.version());
        return ApiResponse.ok(toPlanDetail(dh));
    }

    // --- 设备计划 ---
    @GetMapping("/device-plans/pending")
    public ApiResponse<List<PlanDetailResponse>> pendingDevicePlans(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") long afterVersion) {
        List<DecisionHistory> plans = devicePlanService.getPendingSync(userId, afterVersion);
        return ApiResponse.ok(plans.stream().map(this::toPlanDetail).toList());
    }

    @PostMapping("/device-plans/{planId}/ack")
    public ApiResponse<PlanDetailResponse> ack(@PathVariable String planId,
                                                @Valid @RequestBody AckRequest req) {
        DecisionHistory dh = devicePlanService.ack(
                planId, req.deviceId(), req.planVersion(),
                req.localAlarmId(), req.scheduledWakeAt());
        return ApiResponse.ok(toPlanDetail(dh));
    }

    // --- 设备事件 ---
    @PostMapping("/device-events")
    public ApiResponse<Void> reportEvent(@Valid @RequestBody DeviceEventRequest req) {
        // MVP: 记录事件日志，后续阶段接入 WakeHistory
        return ApiResponse.ok(null);
    }

    // --- 晨间简报 ---
    @GetMapping("/morning/context")
    public ApiResponse<MorningContextResponse> morningContext(@RequestParam String userId) {
        List<DecisionHistory> pending = decisionService.getPendingPlans(userId);
        DecisionHistory latest = pending.isEmpty() ? null : pending.get(0);

        return ApiResponse.ok(new MorningContextResponse(
                latest != null ? new WakePlanInfo(latest.getRecommendedAt(), null, latest.getBaselineAt()) : null,
                null, // weather summary - MVP 简化
                null, // traffic - MVP 不做
                null, // first event
                Instant.now()
        ));
    }

    // --- DTO 映射 ---
    private PlanDetailResponse toPlanDetail(DecisionHistory dh) {
        return new PlanDetailResponse(
                dh.getPlanId(), dh.getAlarmId(), dh.getStatus().name(),
                dh.getBaselineAt(), dh.getRecommendedAt(),
                dh.getEffectiveAt(), dh.getBaselineAt(),
                new RiskInfo(dh.getRiskScore().doubleValue(),
                        dh.getLateProbability().doubleValue(),
                        dh.getConfidence().doubleValue()),
                dh.getFactorContributionJson(),
                dh.getExplanation(),
                dh.getReasonCode(),
                dh.getCreatedAt(),
                dh.getCreatedAt(), // dataFreshness simplified
                dh.getStatus() == DecisionHistory.DecisionStatus.PENDING,
                dh.getCreatedAt().plusSeconds(3600), // expiresAt
                dh.getVersion()
        );
    }

    // --- DTO records ---
    public record PreviewRequest(@NotBlank String geoHash) {}
    public record PlanPreviewResponse(Instant baselineWakeAt, Instant recommendedWakeAt,
                                       int deltaMinutes, double lateProbability,
                                       double confidence, Instant expiresAt) {}

    public record EvaluateRequest(@NotBlank String alarmId, @NotNull Instant targetDate, String geoHash) {}
    public record EvaluateResponse(String jobId, String status) {}

    public record ConfirmRequest(@NotNull long version) {}
    public record RevertRequest(@NotNull long version) {}

    public record AckRequest(@NotBlank String deviceId, @NotNull long planVersion,
                              @NotBlank String localAlarmId, @NotNull Instant scheduledWakeAt) {}

    public record DeviceEventRequest(@NotBlank String eventId, @NotBlank String planId,
                                      @NotBlank String type, @NotNull Instant occurredAt) {}

    public record PlanDetailResponse(
            String planId, String alarmId, String status,
            Instant baselineWakeAt, Instant recommendedWakeAt,
            Instant effectiveAt, Instant targetArrivalAt,
            RiskInfo risk, String topFactors, String explanation,
            String reasonCode, Instant createdAt, Instant dataFreshness,
            boolean requiresConfirmation, Instant expiresAt, long version
    ) {}
    public record RiskInfo(double score, double lateProbability, double confidence) {}

    public record MorningContextResponse(
            WakePlanInfo wakePlan, Object weather, Object traffic,
            Object firstEvent, Instant updatedAt
    ) {}
    public record WakePlanInfo(Instant wakeAt, Instant leaveBy, Instant arriveBy) {}
}