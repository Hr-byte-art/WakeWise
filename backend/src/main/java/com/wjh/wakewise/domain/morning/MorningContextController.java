package com.wjh.wakewise.domain.morning;

import com.wjh.wakewise.common.ApiResponse;
import com.wjh.wakewise.common.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/morning")
public class MorningContextController {

    private final MorningContextService morningContextService;

    public MorningContextController(MorningContextService morningContextService) {
        this.morningContextService = morningContextService;
    }

    @GetMapping("/context")
    public ApiResponse<MorningContextService.MorningContextView> context(
            @RequestParam(required = false) String date) {
        LocalDate parsed = date == null || date.isBlank() ? null : LocalDate.parse(date);
        return ApiResponse.ok(morningContextService.forUser(AuthContext.currentUserId(), parsed));
    }
}
