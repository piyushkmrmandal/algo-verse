package com.algoverse.notification.service.impl;

import com.algoverse.notification.domain.InAppNotification;
import com.algoverse.notification.repository.InAppNotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InAppNotificationServiceImpl")
class InAppNotificationServiceImplTest {

    @Mock InAppNotificationRepository repository;
    @InjectMocks InAppNotificationServiceImpl service;

    private static final String USER_ID  = "user-99";
    private static final String TYPE     = "streak.updated";
    private static final String TITLE    = "7-day streak!";
    private static final String BODY     = "Keep it up!";
    private static final String IDEM_KEY = "streak-007";

    // ── Happy-path persistence ─────────────────────────────────────────────────

    @Nested
    @DisplayName("when no duplicate exists")
    class NoDuplicate {

        @Test
        @DisplayName("saves notification with correct field values")
        void persist_savesEntity() {
            when(repository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());

            service.persist(USER_ID, TYPE, TITLE, BODY, Map.of("streak", 7), IDEM_KEY);

            ArgumentCaptor<InAppNotification> captor = ArgumentCaptor.forClass(InAppNotification.class);
            verify(repository).save(captor.capture());

            InAppNotification saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getType()).isEqualTo(TYPE);
            assertThat(saved.getTitle()).isEqualTo(TITLE);
            assertThat(saved.getBody()).isEqualTo(BODY);
            assertThat(saved.getIdempotencyKey()).isEqualTo(IDEM_KEY);
            assertThat(saved.getData()).containsEntry("streak", 7);
        }

        @Test
        @DisplayName("truncates title longer than 80 chars")
        void persist_truncatesLongTitle() {
            when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            String longTitle = "A".repeat(100);

            service.persist(USER_ID, TYPE, longTitle, BODY, Map.of(), IDEM_KEY);

            ArgumentCaptor<InAppNotification> captor = ArgumentCaptor.forClass(InAppNotification.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getTitle()).hasSize(80);
        }

        @Test
        @DisplayName("truncates body longer than 500 chars")
        void persist_truncatesLongBody() {
            when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            String longBody = "B".repeat(600);

            service.persist(USER_ID, TYPE, TITLE, longBody, Map.of(), IDEM_KEY);

            ArgumentCaptor<InAppNotification> captor = ArgumentCaptor.forClass(InAppNotification.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getBody()).hasSize(500);
        }

        @Test
        @DisplayName("exact 80-char title is not truncated")
        void persist_exactBoundaryTitle() {
            when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            String exactTitle = "X".repeat(80);

            service.persist(USER_ID, TYPE, exactTitle, BODY, Map.of(), IDEM_KEY);

            ArgumentCaptor<InAppNotification> captor = ArgumentCaptor.forClass(InAppNotification.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getTitle()).hasSize(80);
        }
    }

    // ── Idempotency ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("when duplicate already exists")
    class DuplicateExists {

        @Test
        @DisplayName("skips save — repository.save never called")
        void persist_skipsOnDuplicate() {
            InAppNotification existing = InAppNotification.builder()
                    .userId(USER_ID).type(TYPE).title(TITLE).body(BODY)
                    .idempotencyKey(IDEM_KEY).build();

            when(repository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.of(existing));

            service.persist(USER_ID, TYPE, TITLE, BODY, Map.of(), IDEM_KEY);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("idempotency check uses exact key passed in")
        void persist_checksCorrectKey() {
            when(repository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
            service.persist(USER_ID, TYPE, TITLE, BODY, Map.of(), IDEM_KEY);

            verify(repository).findByIdempotencyKey(IDEM_KEY);
        }
    }
}
