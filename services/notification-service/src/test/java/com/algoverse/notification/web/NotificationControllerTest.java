package com.algoverse.notification.web;

import com.algoverse.notification.domain.InAppNotification;
import com.algoverse.notification.repository.InAppNotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@DisplayName("NotificationController")
@WithMockUser
class NotificationControllerTest {

    @Autowired MockMvc mvc;
    @MockBean  InAppNotificationRepository repository;

    private static final String USER_ID = "user-007";

    // ── GET /{userId} ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{userId}")
    class GetNotifications {

        @Test
        @DisplayName("returns 200 with paginated notifications")
        void getNotifications_200() throws Exception {
            InAppNotification n = InAppNotification.builder()
                    .userId(USER_ID).type("badge.earned")
                    .title("Gold Badge").body("Nice work").idempotencyKey("k1")
                    .build();

            when(repository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(n), PageRequest.of(0, 20), 1));

            mvc.perform(get("/api/v1/notifications/{userId}", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].type").value("badge.earned"))
                    .andExpect(jsonPath("$.content[0].title").value("Gold Badge"));
        }

        @Test
        @DisplayName("returns empty page when user has no notifications")
        void getNotifications_emptyPage() throws Exception {
            when(repository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mvc.perform(get("/api/v1/notifications/{userId}", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("respects page and size query params")
        void getNotifications_paginationForwarded() throws Exception {
            when(repository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), eq(PageRequest.of(2, 10))))
                    .thenReturn(new PageImpl<>(List.of()));

            mvc.perform(get("/api/v1/notifications/{userId}", USER_ID)
                            .param("page", "2").param("size", "10"))
                    .andExpect(status().isOk());

            verify(repository).findByUserIdOrderByCreatedAtDesc(USER_ID, PageRequest.of(2, 10));
        }
    }

    // ── GET /{userId}/unread-count ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{userId}/unread-count")
    class UnreadCount {

        @Test
        @DisplayName("returns count as JSON object")
        void unreadCount_200() throws Exception {
            when(repository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(5L);

            mvc.perform(get("/api/v1/notifications/{userId}/unread-count", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));
        }

        @Test
        @DisplayName("returns 0 when all notifications are read")
        void unreadCount_zero() throws Exception {
            when(repository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(0L);

            mvc.perform(get("/api/v1/notifications/{userId}/unread-count", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0));
        }
    }

    // ── POST /{userId}/mark-all-read ───────────────────────────────────────────

    @Nested
    @DisplayName("POST /{userId}/mark-all-read")
    class MarkAllRead {

        @Test
        @DisplayName("returns 200 with updated count")
        void markAllRead_200() throws Exception {
            when(repository.markAllReadForUser(USER_ID)).thenReturn(3);

            mvc.perform(post("/api/v1/notifications/{userId}/mark-all-read", USER_ID)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(3));
        }

        @Test
        @DisplayName("returns 0 when nothing was unread")
        void markAllRead_nothingUpdated() throws Exception {
            when(repository.markAllReadForUser(USER_ID)).thenReturn(0);

            mvc.perform(post("/api/v1/notifications/{userId}/mark-all-read", USER_ID)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(0));
        }
    }

    // ── DELETE /{notificationId} ───────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /{notificationId}")
    class DeleteNotification {

        @Test
        @DisplayName("returns 204 No Content on successful delete")
        void delete_204() throws Exception {
            UUID id = UUID.randomUUID();
            doNothing().when(repository).deleteById(id);

            mvc.perform(delete("/api/v1/notifications/{notificationId}", id)
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            verify(repository).deleteById(id);
        }
    }
}
