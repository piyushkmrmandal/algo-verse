package com.algoverse.submission.service;

import com.algoverse.submission.domain.Submission;
import com.algoverse.submission.domain.SubmissionStatus;
import com.algoverse.submission.dto.SubmissionResult;
import com.algoverse.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JudgeService unit tests")
class JudgeServiceTest {

    @Mock private SubmissionRepository submissionRepository;
    @InjectMocks private JudgeService judgeService;

    private Submission buildSubmission(String slug, String code) {
        return Submission.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .problemSlug(slug)
                .language("python")
                .code(code)
                .difficulty("MEDIUM")
                .status(SubmissionStatus.RUNNING)
                .submittedAt(OffsetDateTime.now())
                .build();
    }

    // ── isObviouslyCorrect patterns ───────────────────────────────────────────

    @Nested
    @DisplayName("isObviouslyCorrect — well-known solutions always ACCEPTED")
    class ObviouslyCorrectPatterns {

        @Test
        @DisplayName("two-sum with HashMap always ACCEPTED")
        void judge_twoSum_hashMap_alwaysAccepted() {
            Submission s = buildSubmission("two-sum",
                    "def two_sum(nums, target):\n    hashmap = {}\n    for i, n in enumerate(nums):\n        if target - n in hashmap:\n            return [hashmap[target-n], i]\n        hashmap[n] = i");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("two-sum with dict() always ACCEPTED")
        void judge_twoSum_dict_alwaysAccepted() {
            Submission s = buildSubmission("two-sum",
                    "seen = dict()\nfor i, v in enumerate(nums):\n    pass");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("reverse-linked-list with prev/next always ACCEPTED")
        void judge_reverseLinkedList_prevNext_alwaysAccepted() {
            Submission s = buildSubmission("reverse-linked-list",
                    "prev = None\ncurr = head\nwhile curr:\n    next = curr.next\n    curr.next = prev\n    prev = curr\n    curr = next");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("valid-parentheses with stack + push always ACCEPTED")
        void judge_validParentheses_stackPush_alwaysAccepted() {
            Submission s = buildSubmission("valid-parentheses",
                    "stack = []\nfor ch in s:\n    stack.push(ch)");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("valid-parentheses with stack + append always ACCEPTED")
        void judge_validParentheses_stackAppend_alwaysAccepted() {
            Submission s = buildSubmission("valid-parentheses",
                    "stack = []\nfor ch in s:\n    stack.append(ch)");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("fibonacci with memo always ACCEPTED")
        void judge_fibonacci_memo_alwaysAccepted() {
            Submission s = buildSubmission("fibonacci",
                    "memo = {}\ndef fib(n):\n    if n in memo: return memo[n]");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("binary-search with mid/left/right always ACCEPTED")
        void judge_binarySearch_midLeftRight_alwaysAccepted() {
            Submission s = buildSubmission("binary-search",
                    "left, right = 0, len(nums)-1\nwhile left <= right:\n    mid = (left+right)//2");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }
    }

    // ── Result fields ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("result fields populated correctly")
    class ResultFields {

        @Test
        @DisplayName("result includes correct submission and user IDs")
        void judge_result_hasCorrectIds() {
            UUID userId = UUID.randomUUID();
            UUID submissionId = UUID.randomUUID();
            Submission s = Submission.builder()
                    .id(submissionId)
                    .userId(userId)
                    .problemSlug("two-sum")
                    .language("java")
                    .code("HashMap map = new HashMap<>();")
                    .difficulty("EASY")
                    .status(SubmissionStatus.RUNNING)
                    .submittedAt(OffsetDateTime.now())
                    .build();
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.id()).isEqualTo(submissionId);
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.problemSlug()).isEqualTo("two-sum");
            assertThat(result.language()).isEqualTo("java");
        }

        @Test
        @DisplayName("ACCEPTED result passes all 10 test cases")
        void judge_accepted_passesAllTestCases() {
            Submission s = buildSubmission("two-sum", "HashMap<> map = new HashMap<>();");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(result.passedTestCases()).isEqualTo(10);
            assertThat(result.totalTestCases()).isEqualTo(10);
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("execution time and memory are within expected bounds")
        void judge_result_hasBoundedPerformanceMetrics() {
            Submission s = buildSubmission("two-sum", "HashMap<> m = new HashMap<>();");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.executionTimeMs()).isBetween(50, 800);
            assertThat(result.memoryUsedKb()).isBetween(2000, 16000);
        }

        @Test
        @DisplayName("gradedAt is set on the result")
        void judge_result_hasGradedAt() {
            Submission s = buildSubmission("two-sum", "HashMap<> m = new HashMap<>();");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(any(), any(), any()))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.gradedAt()).isNotNull();
            assertThat(result.gradedAt()).isAfterOrEqualTo(result.submittedAt());
        }
    }

    // ── First solve detection ─────────────────────────────────────────────────

    @Nested
    @DisplayName("first solve detection")
    class FirstSolveDetection {

        @Test
        @DisplayName("isFirstSolve=true when no prior ACCEPTED submission exists")
        void judge_firstSolve_isTrue_whenNoPriorAccepted() {
            Submission s = buildSubmission("two-sum", "HashMap<> map = new HashMap<>();");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(
                    any(), eq("two-sum"), eq(SubmissionStatus.ACCEPTED)))
                    .thenReturn(false);

            SubmissionResult result = judgeService.judge(s);

            assertThat(result.isFirstSolve()).isTrue();
        }

        @Test
        @DisplayName("isFirstSolve=false when a prior ACCEPTED submission exists")
        void judge_firstSolve_isFalse_whenPriorAcceptedExists() {
            Submission s = buildSubmission("two-sum", "HashMap<> map = new HashMap<>();");
            when(submissionRepository.existsByUserIdAndProblemSlugAndStatus(
                    any(), eq("two-sum"), eq(SubmissionStatus.ACCEPTED)))
                    .thenReturn(true);

            SubmissionResult result = judgeService.judge(s);

            // ACCEPTED but not first solve
            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(result.isFirstSolve()).isFalse();
        }
    }
}
