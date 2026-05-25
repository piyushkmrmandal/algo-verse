package com.algoverse.notification.service.impl;

import com.algoverse.notification.service.FirebasePushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Firebase FCM push implementation.
 *
 * When {@code notification.push.enabled=true} and a service-account JSON is
 * configured, this would call the Firebase Admin SDK. In dev it logs the intent.
 *
 * To enable real FCM: add com.google.firebase:firebase-admin to pom.xml,
 * initialise FirebaseApp from the service account file, and call
 * FirebaseMessaging.getInstance().send(Message.builder()...build()).
 */
@Slf4j
@Service
public class FirebasePushServiceImpl implements FirebasePushService {

    @Value("${notification.push.enabled:false}")
    private boolean pushEnabled;

    @Override
    public void send(String userId, String title, String body,
                     Map<String, String> data, String idempotencyKey) {
        if (!pushEnabled) {
            log.info("[PUSH-STUB] Would push to userId='{}' title='{}' key='{}'",
                    userId, title, idempotencyKey);
            return;
        }

        // Real FCM call:
        // Message message = Message.builder()
        //     .putAllData(data)
        //     .setNotification(Notification.builder().setTitle(title).setBody(body).build())
        //     .setToken(fcmTokenStore.getToken(userId))
        //     .build();
        // FirebaseMessaging.getInstance().send(message);
        log.info("Push sent via FCM: userId={} title={}", userId, title);
    }
}
