package com.algoverse.notification.service.impl;

import com.algoverse.notification.service.FirebasePushService;
import com.algoverse.notification.service.InAppNotificationService;
import com.algoverse.notification.service.SesEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl")
class NotificationServiceImplTest {

    @Mock SesEmailService sesEmailService;
    @Mock FirebasePushService firebasePushService;
    @Mock InAppNotificationService inAppNotificationService;

    @InjectMocks NotificationServiceImpl service;

    private static final String USER_ID       = "user-42";
    private static final String IDEM_KEY      = "idem-001";
    private static final String TYPE          = "badge.earned";
    private static final Map<String, Object> DATA = Map.of(
            "title", "First Solve",
            "body",  "You solved your first problem!",
            "email", "user@example.com"
    );

    // ── EMAIL ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EMAIL channel")
    class EmailChannel {

        @Test
        @DisplayName("delegates to SesEmailService with :email suffix on idempotency key")
        void dispatchEmail_callsSes() {
            service.dispatch(USER_ID, "EMAIL", TYPE, DATA, IDEM_KEY);

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(sesEmailService).send(eq("user@example.com"), eq("badge-earned"), eq(DATA), keyCaptor.capture());
            assertThat(keyCaptor.getValue()).isEqualTo(IDEM_KEY + ":email");

            verifyNoInteractions(firebasePushService, inAppNotificationService);
        }

        @Test
        @DisplayName("falls back to synthesized email when 'email' key absent from data")
        void dispatchEmail_synthesizesEmailWhenMissing() {
            service.dispatch(USER_ID, "EMAIL", TYPE, Map.of(), IDEM_KEY);

            ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
            verify(sesEmailService).send(toCaptor.capture(), any(), any(), any());
            assertThat(toCaptor.getValue()).contains(USER_ID);
        }

        @Test
        @DisplayName("channel matching is case-insensitive")
        void dispatchEmail_caseInsensitive() {
            service.dispatch(USER_ID, "email", TYPE, DATA, IDEM_KEY);
            verify(sesEmailService).send(any(), any(), any(), any());
        }
    }

    // ── PUSH ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUSH channel")
    class PushChannel {

        @Test
        @DisplayName("delegates to FirebasePushService with :push suffix")
        void dispatchPush_callsFirebase() {
            service.dispatch(USER_ID, "PUSH", TYPE, DATA, IDEM_KEY);

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(firebasePushService).send(
                    eq(USER_ID),
                    eq("First Solve"),
                    eq("You solved your first problem!"),
                    any(),
                    keyCaptor.capture()
            );
            assertThat(keyCaptor.getValue()).isEqualTo(IDEM_KEY + ":push");

            verifyNoInteractions(sesEmailService, inAppNotificationService);
        }

        @Test
        @DisplayName("uses 'type' as title fallback when 'title' absent")
        void dispatchPush_usesTypeFallback() {
            service.dispatch(USER_ID, "PUSH", TYPE, Map.of(), IDEM_KEY);

            ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
            verify(firebasePushService).send(any(), titleCaptor.capture(), any(), any(), any());
            assertThat(titleCaptor.getValue()).isEqualTo(TYPE);
        }
    }

    // ── IN_APP ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("IN_APP channel")
    class InAppChannel {

        @Test
        @DisplayName("delegates to InAppNotificationService with :inapp suffix")
        void dispatchInApp_callsService() {
            service.dispatch(USER_ID, "IN_APP", TYPE, DATA, IDEM_KEY);

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(inAppNotificationService).persist(
                    eq(USER_ID), eq(TYPE),
                    eq("First Solve"),
                    eq("You solved your first problem!"),
                    eq(DATA),
                    keyCaptor.capture()
            );
            assertThat(keyCaptor.getValue()).isEqualTo(IDEM_KEY + ":inapp");

            verifyNoInteractions(sesEmailService, firebasePushService);
        }
    }

    // ── ALL ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ALL channel")
    class AllChannel {

        @Test
        @DisplayName("fans out to all three channels")
        void dispatchAll_callsAllThree() {
            service.dispatch(USER_ID, "ALL", TYPE, DATA, IDEM_KEY);

            verify(sesEmailService).send(any(), any(), any(), contains(":email"));
            verify(firebasePushService).send(any(), any(), any(), any(), contains(":push"));
            verify(inAppNotificationService).persist(any(), any(), any(), any(), any(), contains(":inapp"));
        }

        @Test
        @DisplayName("each sub-dispatch uses unique idempotency suffix")
        void dispatchAll_uniqueSuffixes() {
            service.dispatch(USER_ID, "ALL", TYPE, DATA, IDEM_KEY);

            ArgumentCaptor<String> emailKey = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> pushKey  = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> inappKey = ArgumentCaptor.forClass(String.class);

            verify(sesEmailService).send(any(), any(), any(), emailKey.capture());
            verify(firebasePushService).send(any(), any(), any(), any(), pushKey.capture());
            verify(inAppNotificationService).persist(any(), any(), any(), any(), any(), inappKey.capture());

            assertThat(emailKey.getValue()).endsWith(":email");
            assertThat(pushKey.getValue()).endsWith(":push");
            assertThat(inappKey.getValue()).endsWith(":inapp");
        }
    }

    // ── UNKNOWN ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unknown channel")
    class UnknownChannel {

        @Test
        @DisplayName("logs a warning and does not call any service")
        void unknownChannel_noServiceInvoked() {
            service.dispatch(USER_ID, "SMOKE_SIGNAL", TYPE, DATA, IDEM_KEY);

            verifyNoInteractions(sesEmailService, firebasePushService, inAppNotificationService);
        }
    }
}
