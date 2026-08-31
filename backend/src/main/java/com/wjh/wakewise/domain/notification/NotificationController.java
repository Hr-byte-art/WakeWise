package com.wjh.wakewise.domain.notification;

import com.wjh.wakewise.common.ApiResponse;
import com.wjh.wakewise.common.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public record NotificationView(String id, String decisionId, String type, String title,
                                   String body, boolean read, String createdAt) {

        public static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getDecisionId(), n.getType(), n.getTitle(),
                    n.getBody(), n.getReadAt() != null, n.getCreatedAt().toString());
        }
    }

    @GetMapping
    public ApiResponse<List<NotificationView>> list() {
        return ApiResponse.ok(notificationService.list(AuthContext.currentUserId()).stream()
                .map(NotificationView::from)
                .toList());
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable String id) {
        notificationService.markRead(AuthContext.currentUserId(), id);
        return ApiResponse.ok();
    }
}
