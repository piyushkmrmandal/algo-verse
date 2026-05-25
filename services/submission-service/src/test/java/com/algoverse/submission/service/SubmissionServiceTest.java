package com.algoverse.submission.service;

import com.algoverse.submission.domain.Submission;
import com.algoverse.submission.domain.SubmissionStatus;
import com.algoverse.submission.dto.*;
import com.algoverse.submission.kafka.SubmissionEventProducer;
import com.algoverse.submission.repository.SubmissionRepository;
import com.algoverse.submission.websocket.SubmissionWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionService unit tests")
class SubmissionServiceTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock private SubmissionRepository submissionRepository;
    @Mock private JudgeService judgeService;
    @Mock private RateLimitService rateLimitService;
    @Mock private SubmissionEventProducer eventProducer;
    @Mock private SubmissionWebSocketHandler webSocketHandler;

    @InjectMocks private SubmissionService submissionService;

    // ── Test data ─────────────────────────────────────────────────────────────

    private UUID userId;
    private UUID submissionId;
    private SubmitRequest validRequest;
    private Submission pendingSubmission;

    @BeforeEach
    void setUp() {
        userId       = UUID.randomUUID();
        submissionId = UUID.randomUUID();

        validRequest = new SubmitRequest("two-sum", "java",
                "public int[] twoSum(int[] nums, int target) { return new int[]{0,1}; }",
                "EASY");

        pendingSubmission = Submission.builder()
                .id(submissionId)
                .userId(userId)
                .problemSlug("two-sum")
                .language("java")
                .code(validRequest.code())
                .difficulty("EASY")
                .status(SubmissionStatus.PENDING)
                .submittedAt(OffsetDateTime.now())
                .build();
    }

    // ── submit tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("submit_underRateLimit_returnsPending: should return PENDING response when under rate limit")
    void submit_underRateLimit_returnsPending() {
        // Arrange
        doNothing().when(rateLimitService).checkAndIncrement(userId);
        when(submissionRepository.save(any(Submission.class))).thenReturn(pendingSubmission);

        // Act
        SubmissionResponse response = submissionService.submit(validRequest, userId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(response.problemSlug()).isEqualTo("two-sum");
        assertThat(response.language()).isEqualTo("java");
        assertThat(response.id()).isEqualTo(submissionId);

        verify(rateLimitService).checkAndIncrement(userId);
        verify(submissionRepository).save(any(Submission.class));
    }

    @Test
    @DisplayName("submit_overRateLimit_throws429: should propagate 429 when rate limit is exceeded")
    void submit_overRateLimit_throws429() {
        // Arrange
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"))
                .when(rateLimitService).checkAndIncrement(userId);

        // Act & Assert
        assertThatThrownBy(() -> submissionService.submit(validRequest, userId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                });

        verify(submissionRepository, never()).save(any());
    }

    // ── getSubmission tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("getSubmission_owned_returnsResult: owner can retrieve their submission")
    void getSubmission_owned_returnsResult() {
        // Arrange
        Submission gradedSubmission = Submission.builder()
                .id(submissionId)
                .userId(userId)
                .problemSlug("two-sum")
                .language("java")
                .code("solution")
                .difficulty("EASY")
                .status(SubmissionStatus.ACCEPTED)
                .executionTimeMs(42)
                .memoryUsedKb(4096)
                .passedTestCases(10)
                .totalTestCases(10)
                .isFirstSolve(true)
                .submittedAt(OffsetDateTime.now())
                .gradedAt(OffsetDateTime.now())
                .build();

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(gradedSubmission));

        // Act
        SubmissionResult result = submissionService.getSubmission(submissionId, userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(submissionId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(result.executionTimeMs()).isEqualTo(42);
        assertThat(result.isFirstSolve()).isTrue();
    }

    @Test
    @DisplayName("getSubmission_notOwned_throws403: non-owner receives 403 Forbidden")
    void getSubmission_notOwned_throws403() {
        // Arrange
        UUID differentUserId = UUID.randomUUID();
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(pendingSubmission));

        // Act & Assert
        assertThatThrownBy(() -> submissionService.getSubmission(submissionId, differentUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    // ── gradeAsync / judge tests ──────────────────────────────────────────────

    @Test
    @DisplayName("judge_accepted_publishesKafkaEvent: ACCEPTED result triggers Kafka event")
    void judge_accepted_publishesKafkaEvent() throws Exception {
        // Arrange
        Submission runningSubmission = Submission.builder()
                .id(submissionId)
                .userId(userId)
                .problemSlug("two-sum")
                .language("java")
                .code("solution")
                .difficulty("EASY")
                .status(SubmissionStatus.RUNNING)
                .submittedAt(OffsetDateTime.now())
                .build();

        SubmissionResult acceptedResult = new SubmissionResult(
                submissionId, userId, "two-sum", "java",
                SubmissionStatus.ACCEPTED, "EASY",
                42, 4096, 10, 10, null, false,
                OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(pendingSubmission));
        when(submissionRepository.save(any(Submission.class))).thenReturn(runningSubmission);
        when(judgeService.judge(any(Submission.class))).thenReturn(acceptedResult);

        // Act
        submissionService.gradeAsync(submissionId).get();

        // Assert — Kafka event must be published for ACCEPTED
        ArgumentCaptor<GradedEvent> eventCaptor = ArgumentCaptor.forClass(GradedEvent.class);
        verify(eventProducer).publishGradedEvent(eventCaptor.capture());

        GradedEvent published = eventCaptor.getValue();
        assertThat(published.submissionId()).isEqualTo(submissionId);
        assertThat(published.userId()).isEqualTo(userId);
        assertThat(published.problemSlug()).isEqualTo("two-sum");
        assertThat(published.status()).isEqualTo("ACCEPTED");
        assertThat(published.difficulty()).isEqualTo("EASY");
    }

    @Test
    @DisplayName("judge_firstSolve_setsIsFirstSolveTrue: isFirstSolve=true when no prior ACCEPTED exists")
    void judge_firstSolve_setsIsFirstSolveTrue() throws Exception {
        // Arrange
        SubmissionResult firstSolveResult = new SubmissionResult(
                submissionId, userId, "two-sum", "java",
                SubmissionStatus.ACCEPTED, "EASY",
                100, 8192, 10, 10, null, true,   // isFirstSolve = true
                OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(pendingSubmission));
        when(submissionRepository.save(any(Submission.class))).thenReturn(pendingSubmission);
        when(judgeService.judge(any(Submission.class))).thenReturn(firstSolveResult);

        // Act
        submissionService.gradeAsync(submissionId).get();

        // Assert — persisted entity must have isFirstSolve=true
        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeast(2)).save(submissionCaptor.capture());

        Submission finalSave = submissionCaptor.getAllValues()
                .stream()
                .filter(s -> s.getStatus() == SubmissionStatus.ACCEPTED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ACCEPTED save found"));

        assertThat(finalSave.getIsFirstSolve()).isTrue();
    }

    @Test
    @DisplayName("judge_subsequentSolve_setsIsFirstSolveFalse: isFirstSolve=false when prior ACCEPTED exists")
    void judge_subsequentSolve_setsIsFirstSolveFalse() throws Exception {
        // Arrange
        SubmissionResult subsequentResult = new SubmissionResult(
                submissionId, userId, "two-sum", "java",
                SubmissionStatus.ACCEPTED, "EASY",
                80, 6144, 10, 10, null, false,   // isFirstSolve = false
                OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(pendingSubmission));
        when(submissionRepository.save(any(Submission.class))).thenReturn(pendingSubmission);
        when(judgeService.judge(any(Submission.class))).thenReturn(subsequentResult);

        // Act
        submissionService.gradeAsync(submissionId).get();

        // Assert — Kafka event published but isFirstSolve=false
        ArgumentCaptor<GradedEvent> eventCaptor = ArgumentCaptor.forClass(GradedEvent.class);
        verify(eventProducer).publishGradedEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().isFirstSolve()).isFalse();

        // And isFirstSolve=false persisted
        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeast(2)).save(submissionCaptor.capture());

        Submission finalSave = submissionCaptor.getAllValues()
                .stream()
                .filter(s -> s.getStatus() == SubmissionStatus.ACCEPTED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ACCEPTED save found"));

        assertThat(finalSave.getIsFirstSolve()).isFalse();
    }
}
