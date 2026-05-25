package com.algoverse.notification.service.impl;

import com.algoverse.notification.domain.InAppNotification;
import com.algoverse.notification.repository.InAppNotificationRepository;
import com.algoverse.notification.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InAppNotificationServiceImpl implements InAppNotificationService {

    private final InAppNotificationRepository repository;

    @Override
    @Transactional
    public void persist(String userId, String type, String title, String body,
                        Map<String, Object> data, String idempotencyKey) {
        // Idempotency check
        if (repository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.debug("Skipping duplicate in-app notification: key={}", idempotencyKey);
            return;
        }

        InAppNotification notification = InAppNotification.builder()
                .userId(userId)
                .type(type)
                .title(title.length() > 80 ? title.substring(0, 80) : title)
                .body(body.length() > 500 ? body.substring(0, 500) : body)
                .data(data)
                .idempotencyKey(idempotencyKey)
                .build();

        repository.save(notification);
        log.info("In-app notification saved: userId={} type={}", userId, type);
    }
}
