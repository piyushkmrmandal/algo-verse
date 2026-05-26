package com.algoverse.notification.integration;

import com.algoverse.notification.domain.InAppNotification;
import com.algoverse.notification.repository.InAppNotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Notification Service — Integration")
class NotificationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("notification_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Override test yml placeholders
        registry.add("TEST_DATASOURCE_URL",      POSTGRES::getJdbcUrl);
        registry.add("TEST_DATASOURCE_USERNAME", POSTGRES::getUsername);
        registry.add("TEST_DATASOURCE_PASSWORD", POSTGRES::getPassword);
        // Disable Kafka bootstrap — consumer will fail to connect but not crash
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired InAppNotificationRepository repository;

    private String base() {
        return "http://localhost:" + port + "/api/v1/notifications";
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    // ── Unread count ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{userId}/unread-count")
    class UnreadCount {

        @Test
        @DisplayName("returns 0 for a user with no notifications")
        void unreadCount_emptyUser() {
            ResponseEntity<Map<String, Long>> resp = rest.exchange(
                    base() + "/new-user/unread-count",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).containsEntry("count", 0L);
        }

        @Test
        @DisplayName("counts only unread notifications for the given user")
        void unreadCount_countsCorrectly() {
            String userId = "u-" + UUID.randomUUID();
            saveNotification(userId, "k1", false);
            saveNotification(userId, "k2", false);
            saveNotification(userId, "k3", true);  // read — must NOT be counted

            ResponseEntity<Map<String, Long>> resp = rest.exchange(
                    base() + "/" + userId + "/unread-count",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).containsEntry("count", 2L);
        }
    }

    // ── Mark all read ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /{userId}/mark-all-read")
    class MarkAllRead {

        @Test
        @DisplayName("marks all unread notifications as read and returns updated count")
        void markAllRead_updatesRows() {
            String userId = "u-" + UUID.randomUUID();
            saveNotification(userId, "mk1", false);
            saveNotification(userId, "mk2", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map<String, Integer>> resp = rest.exchange(
                    base() + "/" + userId + "/mark-all-read",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).containsKey("updated");
            assertThat(resp.getBody().get("updated")).isEqualTo(2);

            // Verify in DB
            assertThat(repository.countByUserIdAndIsReadFalse(userId)).isZero();
        }

        @Test
        @DisplayName("returns 0 when no unread notifications exist")
        void markAllRead_noOp() {
            String userId = "u-" + UUID.randomUUID();
            saveNotification(userId, "read-k", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map<String, Integer>> resp = rest.exchange(
                    base() + "/" + userId + "/mark-all-read",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).containsEntry("updated", 0);
        }
    }

    // ── Delete notification ────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /{notificationId}")
    class DeleteNotification {

        @Test
        @DisplayName("returns 204 and removes the notification from DB")
        void delete_removesEntity() {
            String userId = "u-" + UUID.randomUUID();
            InAppNotification saved = saveNotification(userId, "del-k1", false);

            ResponseEntity<Void> resp = rest.exchange(
                    base() + "/" + saved.getId(),
                    HttpMethod.DELETE, null, Void.class
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(repository.findById(saved.getId())).isEmpty();
        }
    }

    // ── GET /{userId} paginated ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{userId}")
    class GetNotifications {

        @Test
        @DisplayName("returns paginated results sorted newest-first")
        void getNotifications_sorted() {
            String userId = "u-" + UUID.randomUUID();
            saveNotification(userId, "pg1", false);
            saveNotification(userId, "pg2", false);

            ResponseEntity<String> resp = rest.getForEntity(
                    base() + "/" + userId + "?page=0&size=10", String.class
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).contains("\"totalElements\":2");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private InAppNotification saveNotification(String userId, String idemKey, boolean read) {
        InAppNotification n = InAppNotification.builder()
                .userId(userId)
                .type("test.event")
                .title("Test Title")
                .body("Test body")
                .idempotencyKey(idemKey)
                .build();
        n = repository.save(n);
        if (read) {
            repository.markAllReadForUser(userId);
        }
        return n;
    }
}
