package com.wjh.wakewise.domain.alarm;

import com.wjh.wakewise.common.ApiResponse;
import com.wjh.wakewise.common.AuthContext;
import com.wjh.wakewise.domain.decision.DecisionService;
import com.wjh.wakewise.domain.decision.WakePlan;
import com.wjh.wakewise.domain.user.UserAccount;
import com.wjh.wakewise.domain.user.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {

    private final AlarmService alarmService;
    private final DecisionService decisionService;
    private final UserService userService;

    public AlarmController(AlarmService alarmService, DecisionService decisionService, UserService userService) {
        this.alarmService = alarmService;
        this.decisionService = decisionService;
        this.userService = userService;
    }

    @PostMapping
    public ApiResponse<AlarmDtos.AlarmView> create(@Valid @RequestBody AlarmDtos.CreateAlarmRequest request) {
        return ApiResponse.ok(alarmService.create(AuthContext.currentUserId(), request));
    }

    @GetMapping
    public ApiResponse<List<AlarmDtos.AlarmView>> list() {
        return ApiResponse.ok(alarmService.list(AuthContext.currentUserId()));
    }

    @GetMapping("/{alarmId}")
    public ApiResponse<AlarmDtos.AlarmView> get(@PathVariable String alarmId) {
        return ApiResponse.ok(alarmService.get(AuthContext.currentUserId(), alarmId));
    }

    @PatchMapping("/{alarmId}")
    public ApiResponse<AlarmDtos.AlarmView> update(@PathVariable String alarmId,
                                                   @Valid @RequestBody AlarmDtos.UpdateAlarmRequest request) {
        return ApiResponse.ok(alarmService.update(AuthContext.currentUserId(), alarmId, request));
    }

    @DeleteMapping("/{alarmId}")
    public ApiResponse<Void> delete(@PathVariable String alarmId) {
        alarmService.delete(AuthContext.currentUserId(), alarmId);
        return ApiResponse.ok();
    }

    @PostMapping("/{alarmId}/preview")
    public ApiResponse<WakePlan> preview(@PathVariable String alarmId,
                                         @RequestBody(required = false) AlarmDtos.PreviewRequest request) {
        String userId = AuthContext.currentUserId();
        UserAccount user = userService.requireUser(userId);
        Integer override = request == null ? null : request.maxAdvanceMinutes();
        return ApiResponse.ok(decisionService.previewForUser(userId, alarmId, user, override));
    }
}
