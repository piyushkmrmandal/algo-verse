package com.algoverse.notification.service.impl;

import com.algoverse.notification.service.FirebasePushService;
import com.algoverse.notification.service.InAppNotificationService;
import com.algoverse.notification.service.NotificationService;
import com.algoverse.notification.service.SesEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final SesEmailService sesEmailService;
    private final FirebasePushService firebasePushService;
    private final InAppNotificationService inAppNotificationService;

    @Override
    public void dispatch(String userId, String channel, String type,
                         Map<String, Object> data, String idempotencyKey) {
        switch (channel.toUpperCase()) {
            case "EMAIL" -> {
                String toAddress = resolveEmail(userId, data);
                String template = type.replace(".", "-");
                sesEmailService.send(toAddress, template, data, idempotencyKey + ":email");
            }
            case "PUSH" -> {
                String title = String.valueOf(data.getOrDefault("title", type));
                String body  = String.valueOf(data.getOrDefault("body", ""));
                Map<String, String> strData = data.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> String.valueOf(e.getValue())
                        ));
                firebasePushService.send(userId, title, body, strData, idempotencyKey + ":push");
            }
            case "IN_APP" -> {
                String title = String.valueOf(data.getOrDefault("title", type));
                String body  = String.valueOf(data.getOrDefault("body", ""));
                inAppNotificationService.persist(userId, type, title, body, data, idempotencyKey + ":inapp");
            }
            case "ALL" -> {
                dispatch(userId, "EMAIL", type, data, idempotencyKey);
                dispatch(userId, "PUSH",   type, data, idempotencyKey);
                dispatch(userId, "IN_APP", type, data, idempotencyKey);
            }
            default -> log.warn("Unknown notification channel: {} for userId={}", channel, userId);
        }
    }

    private String resolveEmail(String userId, Map<String, Object> data) {
        Object email = data.get("email");
        if (email != null) return String.valueOf(email);
        // Fallback: synthesize placeholder; real impl queries user-profile cache
        return "user+" + userId + "@users.algoverse.io";
    }
}
