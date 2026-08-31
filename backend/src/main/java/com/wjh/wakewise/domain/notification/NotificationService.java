package com.wjh.wakewise.domain.notification;

import com.wjh.wakewise.common.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void create(String userId, String decisionId, String type, String title, String body) {
        Notification notification = new Notification();
        notification.setId(Ids.newId());
        notification.setUserId(userId);
        notification.setDecisionId(decisionId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        repository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> list(String userId) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markRead(String userId, String id) {
        repository.findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(n -> n.setReadAt(Instant.now()));
    }
}
