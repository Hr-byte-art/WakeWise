package com.wjh.backend.domain.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepo;

    public NotificationService(NotificationRepository notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    public Notification send(String userId, String type, String title, String body) {
        Notification n = Notification.create(userId, type, title, body);
        n.setStatus(Notification.NotificationStatus.SENT);
        n.setSentAt(Instant.now());
        log.info("通知已发送 [{}] -> user={}, title={}", type, userId, title);
        return notificationRepo.save(n);
    }

    public Notification sendWithDecision(String userId, String decisionId, String type, String title, String body) {
        Notification n = Notification.create(userId, type, title, body);
        n.setDecisionId(decisionId);
        n.setStatus(Notification.NotificationStatus.SENT);
        n.setSentAt(Instant.now());
        log.info("决策通知已发送 [{}] -> user={}, decision={}, title={}", type, userId, decisionId, title);
        return notificationRepo.save(n);
    }
}