package com.wjh.wakewise.domain.decision;

import com.wjh.wakewise.common.ApiResponse;
import com.wjh.wakewise.common.AuthContext;
import com.wjh.wakewise.domain.behavior.BehaviorLearningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DevicePlanController {

    private static final Logger log = LoggerFactory.getLogger(DevicePlanController.class);

    private final DecisionService decisionService;
    private final BehaviorLearningService behaviorLearningService;

    public DevicePlanController(DecisionService decisionService,
                                BehaviorLearningService behaviorLearningService) {
        this.decisionService = decisionService;
        this.behaviorLearningService = behaviorLearningService;
    }

    @GetMapping("/device-plans/pending")
    public ApiResponse<List<DecisionDtos.PendingPlanView>> pending() {
        return ApiResponse.ok(decisionService.pending(AuthContext.currentUserId()));
    }

    @PostMapping("/device-plans/{planId}/ack")
    public ApiResponse<DecisionDtos.PlanView> ack(@PathVariable String planId,
                                     @RequestBody(required = false) DecisionDtos.AckRequest request) {
        return ApiResponse.ok(decisionService.ack(AuthContext.currentUserId(), planId));
    }

    @PostMapping("/device-events")
    public ApiResponse<Void> recordEvent(@RequestBody DecisionDtos.DeviceEventRequest request) {
        String userId = AuthContext.currentUserId();
        decisionService.recordDeviceEvent(userId, request);
        // 关闭事件落库后重算起床延迟校准；出门事件落库后重算准备时长校准。
        // 放在事件事务提交之后触发：校准是增强链路，失败只记日志，
        // 绝不把已提交的事件拖回滚（"AI 可以失败，闹钟不能"）。
        if ("ALARM_DISMISSED".equals(request.type())) {
            try {
                behaviorLearningService.recalibrateWakeLatency(userId);
            } catch (Exception e) {
                log.warn("Wake latency recalibration failed after dismiss: user={}", userId, e);
            }
        } else if ("USER_LEFT_HOME".equals(request.type())) {
            try {
                behaviorLearningService.recalibratePrepDuration(userId);
            } catch (Exception e) {
                log.warn("Prep duration recalibration failed after left-home: user={}", userId, e);
            }
        }
        return ApiResponse.ok();
    }
}
