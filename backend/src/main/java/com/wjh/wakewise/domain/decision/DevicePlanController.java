package com.wjh.wakewise.domain.decision;

import com.wjh.wakewise.common.ApiResponse;
import com.wjh.wakewise.common.AuthContext;
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

    private final DecisionService decisionService;

    public DevicePlanController(DecisionService decisionService) {
        this.decisionService = decisionService;
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
        decisionService.recordDeviceEvent(AuthContext.currentUserId(), request);
        return ApiResponse.ok();
    }
}
