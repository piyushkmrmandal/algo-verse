package com.algoverse.sysdesign.service;

import com.algoverse.sysdesign.domain.DiagramContent;
import com.algoverse.sysdesign.domain.FeedbackRecord;
import com.algoverse.sysdesign.domain.SysdesignProblem;
import com.algoverse.sysdesign.domain.UserDiagram;
import com.algoverse.sysdesign.dto.DiagramDto;
import com.algoverse.sysdesign.dto.DiagramFeedback;
import com.algoverse.sysdesign.dto.SaveDiagramRequest;
import com.algoverse.sysdesign.repository.DiagramContentRepository;
import com.algoverse.sysdesign.repository.FeedbackRecordRepository;
import com.algoverse.sysdesign.repository.SysdesignProblemRepository;
import com.algoverse.sysdesign.repository.UserDiagramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class DiagramServiceTest {

    // ── DiagramService mocks ─────────────────────────────────────
    @Mock private UserDiagramRepository userDiagramRepository;
    @Mock private DiagramContentRepository diagramContentRepository;
    @InjectMocks private DiagramService diagramService;

    // ── AiFeedbackService mocks ──────────────────────────────────
    @Mock private FeedbackRecordRepository feedbackRecordRepository;
    @Mock private SysdesignProblemRepository problemRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @InjectMocks private AiFeedbackService aiFeedbackService;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID OTHER_UID = UUID.randomUUID();
    private static final UUID PROB_ID   = UUID.randomUUID();
    private static final UUID DIAG_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── DiagramService tests ─────────────────────────────────────

    @Test
    @DisplayName("saveDiagram_newDiagram_persistsToBothStores")
    void saveDiagram_newDiagram_persistsToBothStores() {
        SaveDiagramRequest req = new SaveDiagramRequest(
            PROB_ID, "My Solution", List.of(), List.of(), Map.of());

        given(userDiagramRepository.findByUserIdAndProblemId(USER_ID, PROB_ID))
            .willReturn(Optional.empty());

        UserDiagram savedDiagram = UserDiagram.builder()
            .id(DIAG_ID).userId(USER_ID).problemId(PROB_ID)
            .title("My Solution").version(1)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        given(userDiagramRepository.save(any(UserDiagram.class))).willReturn(savedDiagram);

        given(diagramContentRepository.findByDiagramId(DIAG_ID.toString()))
            .willReturn(Optional.empty());
        given(diagramContentRepository.save(any(DiagramContent.class)))
            .willAnswer(inv -> inv.getArgument(0));

        DiagramDto result = diagramService.saveDiagram(USER_ID, req);

        assertThat(result.id()).isEqualTo(DIAG_ID);
        assertThat(result.version()).isEqualTo(1);
        then(userDiagramRepository).should().save(any(UserDiagram.class));
        then(diagramContentRepository).should().save(any(DiagramContent.class));
    }

    @Test
    @DisplayName("saveDiagram_existingDiagram_incrementsVersion")
    void saveDiagram_existingDiagram_incrementsVersion() {
        SaveDiagramRequest req = new SaveDiagramRequest(
            PROB_ID, "Updated", List.of(), List.of(), Map.of());

        UserDiagram existing = UserDiagram.builder()
            .id(DIAG_ID).userId(USER_ID).problemId(PROB_ID)
            .title("Old Title").version(3)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        given(userDiagramRepository.findByUserIdAndProblemId(USER_ID, PROB_ID))
            .willReturn(Optional.of(existing));

        given(userDiagramRepository.save(any(UserDiagram.class)))
            .willAnswer(inv -> inv.getArgument(0));
        given(diagramContentRepository.findByDiagramId(DIAG_ID.toString()))
            .willReturn(Optional.empty());
        given(diagramContentRepository.save(any(DiagramContent.class)))
            .willAnswer(inv -> inv.getArgument(0));

        DiagramDto result = diagramService.saveDiagram(USER_ID, req);

        assertThat(result.version()).isEqualTo(4);
        assertThat(result.title()).isEqualTo("Updated");
    }

    @Test
    @DisplayName("getDiagram_ownedByUser_returnsContent")
    void getDiagram_ownedByUser_returnsContent() {
        UserDiagram diagram = UserDiagram.builder()
            .id(DIAG_ID).userId(USER_ID).problemId(PROB_ID).version(1)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();

        DiagramContent content = DiagramContent.builder()
            .id(DIAG_ID.toString()).diagramId(DIAG_ID.toString())
            .nodes(List.of(new DiagramContent.DiagramNode("n1", "service", "API", 0, 0, Map.of())))
            .edges(List.of())
            .metadata(Map.of("zoom", 1.0))
            .savedAt(Instant.now())
            .build();

        given(userDiagramRepository.findById(DIAG_ID)).willReturn(Optional.of(diagram));
        given(diagramContentRepository.findByDiagramId(DIAG_ID.toString()))
            .willReturn(Optional.of(content));

        DiagramDto result = diagramService.getDiagram(DIAG_ID, USER_ID);

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.metadata()).containsKey("zoom");
    }

    @Test
    @DisplayName("getDiagram_notOwned_throws403")
    void getDiagram_notOwned_throws403() {
        UserDiagram diagram = UserDiagram.builder()
            .id(DIAG_ID).userId(OTHER_UID).problemId(PROB_ID).version(1)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();

        given(userDiagramRepository.findById(DIAG_ID)).willReturn(Optional.of(diagram));

        assertThatThrownBy(() -> diagramService.getDiagram(DIAG_ID, USER_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403");
    }

    @Test
    @DisplayName("deleteDiagram_ownedByUser_removesFromBothStores")
    void deleteDiagram_ownedByUser_removesFromBothStores() {
        UserDiagram diagram = UserDiagram.builder()
            .id(DIAG_ID).userId(USER_ID).problemId(PROB_ID).version(1)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();

        given(userDiagramRepository.findById(DIAG_ID)).willReturn(Optional.of(diagram));

        diagramService.deleteDiagram(DIAG_ID, USER_ID);

        then(diagramContentRepository).should().deleteByDiagramId(DIAG_ID.toString());
        then(userDiagramRepository).should().deleteById(DIAG_ID);
    }

    // ── AiFeedbackService tests ──────────────────────────────────

    @Test
    @DisplayName("generateFeedback_emptyDiagram_returnsLowScore")
    void generateFeedback_emptyDiagram_returnsLowScore() {
        DiagramContent emptyContent = DiagramContent.builder()
            .id("d1").diagramId("d1")
            .nodes(List.of()).edges(List.of())
            .metadata(Map.of()).savedAt(Instant.now())
            .build();

        SysdesignProblem problem = buildProblem("design-url-shortener", "Design a URL Shortener", "EASY");

        FeedbackRecord record = aiFeedbackService.generateMockFeedback(emptyContent, problem, USER_ID.toString());

        assertThat(record.getOverallScore()).isEqualTo(20);
        assertThat(record.getStrengths()).isNotEmpty();
        assertThat(record.getImprovements()).isNotEmpty();
    }

    @Test
    @DisplayName("generateFeedback_richDiagram_returnsHighScore")
    void generateFeedback_richDiagram_returnsHighScore() {
        List<DiagramContent.DiagramNode> nodes = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            nodes.add(new DiagramContent.DiagramNode(
                "n" + i, i % 3 == 0 ? "database" : "service",
                "Component " + i, i * 10.0, i * 10.0, Map.of()));
        }

        DiagramContent richContent = DiagramContent.builder()
            .id("d2").diagramId("d2")
            .nodes(nodes)
            .edges(List.of(
                new DiagramContent.DiagramEdge("e1", "n0", "n1", "calls", "REST"),
                new DiagramContent.DiagramEdge("e2", "n1", "n2", "reads", "SQL"),
                new DiagramContent.DiagramEdge("e3", "n2", "n3", "async", "KAFKA"),
                new DiagramContent.DiagramEdge("e4", "n3", "n4", "stores", "HTTP"),
                new DiagramContent.DiagramEdge("e5", "n4", "n5", "caches", "REDIS"),
                new DiagramContent.DiagramEdge("e6", "n5", "n6", "queries", "HTTP")
            ))
            .metadata(Map.of("zoom", 0.8))
            .savedAt(Instant.now())
            .build();

        SysdesignProblem problem = buildProblem("design-twitter", "Design Twitter", "HARD");

        FeedbackRecord record = aiFeedbackService.generateMockFeedback(richContent, problem, USER_ID.toString());

        assertThat(record.getOverallScore()).isGreaterThanOrEqualTo(90);
        assertThat(record.getStrengths()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("generateFeedback_cached_doesNotRegenerate")
    void generateFeedback_cached_doesNotRegenerate() {
        DiagramFeedback cachedFeedback = new DiagramFeedback(
            "fb-1", DIAG_ID.toString(), 80, "Cached summary",
            List.of("strength"), List.of("improvement"),
            List.of(), Instant.now()
        );

        given(valueOps.get("feedback:" + DIAG_ID)).willReturn(cachedFeedback);

        UserDiagram diagram = UserDiagram.builder()
            .id(DIAG_ID).userId(USER_ID).problemId(PROB_ID).version(1)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        given(userDiagramRepository.findById(DIAG_ID)).willReturn(Optional.of(diagram));

        DiagramFeedback result = aiFeedbackService.reviewDiagram(DIAG_ID, USER_ID);

        assertThat(result.feedbackId()).isEqualTo("fb-1");
        assertThat(result.overallScore()).isEqualTo(80);
        // Should NOT call MongoDB or save anything
        then(feedbackRecordRepository).shouldHaveNoInteractions();
        then(diagramContentRepository).shouldHaveNoInteractions();
    }

    // ── Helpers ──────────────────────────────────────────────────

    private SysdesignProblem buildProblem(String slug, String title, String difficulty) {
        return SysdesignProblem.builder()
            .id(PROB_ID)
            .slug(slug)
            .title(title)
            .difficulty(difficulty)
            .category("Test")
            .descriptionMd("## " + title)
            .requirements(List.of("Req 1", "Req 2"))
            .published(true)
            .createdAt(Instant.now())
            .build();
    }
}
