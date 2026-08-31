package com.wjh.wakewise.domain.decision;

import com.wjh.wakewise.common.ApiResponse;
import com.wjh.wakewise.common.AuthContext;
import com.wjh.wakewise.common.BusinessException;
import com.wjh.wakewise.common.ErrorCode;
import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.alarm.AlarmRepository;
import com.wjh.wakewise.domain.user.UserAccount;
import com.wjh.wakewise.domain.user.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {

    private final DecisionService decisionService;
    private final AlarmRepository alarmRepository;
    private final UserService userService;

    public DecisionController(DecisionService decisionService, AlarmRepository alarmRepository,
                              UserService userService) {
        this.decisionService = decisionService;
        this.alarmRepository = alarmRepository;
        this.userService = userService;
    }

    @PostMapping("/evaluate")
    public ApiResponse<WakePlan> evaluate(@Valid @RequestBody DecisionDtos.EvaluateRequest request) {
        String userId = AuthContext.currentUserId();
        Alarm alarm = alarmRepository.findById(request.alarmId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "闹钟不存在"));
        UserAccount user = userService.requireUser(userId);
        return ApiResponse.ok(decisionService.evaluateAndPropose(alarm, user, "MANUAL"));
    }

    @GetMapping("/{planId}")
    public ApiResponse<DecisionDtos.PlanView> get(@PathVariable String planId) {
        return ApiResponse.ok(decisionService.getByPlanId(AuthContext.currentUserId(), planId));
    }

    @PostMapping("/{planId}/confirm")
    public ApiResponse<DecisionDtos.PlanView> confirm(@PathVariable String planId,
                                         @RequestBody(required = false) DecisionDtos.ConfirmRequest request) {
        return ApiResponse.ok(decisionService.confirm(AuthContext.currentUserId(), planId));
    }

    @PostMapping("/{planId}/revert")
    public ApiResponse<DecisionDtos.PlanView> revert(@PathVariable String planId,
                                        @RequestBody(required = false) DecisionDtos.RevertRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.ok(decisionService.revert(AuthContext.currentUserId(), planId, reason));
    }
}
