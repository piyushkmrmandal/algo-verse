package com.algoverse.sysdesign.service;

import com.algoverse.sysdesign.domain.DiagramContent;
import com.algoverse.sysdesign.domain.FeedbackRecord;
import com.algoverse.sysdesign.domain.SysdesignProblem;
import com.algoverse.sysdesign.domain.UserDiagram;
import com.algoverse.sysdesign.dto.DiagramFeedback;
import com.algoverse.sysdesign.repository.DiagramContentRepository;
import com.algoverse.sysdesign.repository.FeedbackRecordRepository;
import com.algoverse.sysdesign.repository.SysdesignProblemRepository;
import com.algoverse.sysdesign.repository.UserDiagramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiFeedbackService {

    private static final String FEEDBACK_CACHE_PREFIX = "feedback:";
    private static final long FEEDBACK_CACHE_TTL_MINUTES = 10;

    private final UserDiagramRepository userDiagramRepository;
    private final DiagramContentRepository diagramContentRepository;
    private final FeedbackRecordRepository feedbackRecordRepository;
    private final SysdesignProblemRepository problemRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public DiagramFeedback reviewDiagram(UUID diagramId, UUID userId) {
        String cacheKey = FEEDBACK_CACHE_PREFIX + diagramId;

        // Check Redis cache first
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof DiagramFeedback cachedFeedback) {
            log.debug("Cache hit for feedback: {}", cacheKey);
            return cachedFeedback;
        }

        // Verify ownership
        UserDiagram userDiagram = userDiagramRepository.findById(diagramId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram not found"));

        if (!userDiagram.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        // Load diagram content from MongoDB
        DiagramContent content = diagramContentRepository
            .findByDiagramId(diagramId.toString())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram content not found"));

        // Load problem
        SysdesignProblem problem = problemRepository.findById(userDiagram.getProblemId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"));

        // Generate mock feedback (real implementation would call ai-service)
        FeedbackRecord record = generateMockFeedback(content, problem, userId.toString());

        // Save to MongoDB
        feedbackRecordRepository.save(record);

        DiagramFeedback feedback = toDto(record);

        // Cache in Redis for 10 minutes
        redisTemplate.opsForValue().set(cacheKey, feedback, FEEDBACK_CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        log.debug("Generated feedback for diagram {} with score {}", diagramId, record.getOverallScore());
        return feedback;
    }

    FeedbackRecord generateMockFeedback(DiagramContent diagram, SysdesignProblem problem, String userId) {
        int nodeCount = diagram.getNodes() != null ? diagram.getNodes().size() : 0;
        int score = computeScore(nodeCount);

        List<String> strengths = computeStrengths(diagram, nodeCount);
        List<String> improvements = computeImprovements(problem);
        List<FeedbackRecord.ComponentFeedback> componentFeedback = computeComponentFeedback(diagram);

        String summary = buildSummary(score, nodeCount, problem.getTitle());

        return FeedbackRecord.builder()
            .diagramId(diagram.getDiagramId())
            .userId(userId)
            .problemSlug(problem.getSlug())
            .overallScore(score)
            .summary(summary)
            .strengths(strengths)
            .improvements(improvements)
            .componentFeedback(componentFeedback)
            .generatedAt(Instant.now())
            .build();
    }

    private int computeScore(int nodeCount) {
        if (nodeCount == 0) return 20;
        if (nodeCount <= 3) return 40;
        if (nodeCount <= 7) return 65;
        if (nodeCount <= 12) return 80;
        return 90;
    }

    private List<String> computeStrengths(DiagramContent diagram, int nodeCount) {
        List<String> all = new ArrayList<>();
        List<String> result = new ArrayList<>();

        if (nodeCount > 0) {
            all.add("Good attempt at decomposing the system into distinct components");
        }
        if (nodeCount >= 4) {
            all.add("Clear separation between client, server, and data layers");
        }
        if (nodeCount >= 8) {
            all.add("Demonstrates awareness of horizontal scalability patterns");
        }
        if (diagram.getEdges() != null && !diagram.getEdges().isEmpty()) {
            all.add("Data flow between components is explicitly modeled");
        }
        if (diagram.getEdges() != null && diagram.getEdges().size() > 5) {
            all.add("Shows understanding of inter-service communication");
        }

        // Pick up to 3 strengths
        int count = Math.min(3, all.size());
        for (int i = 0; i < count; i++) {
            result.add(all.get(i));
        }
        if (result.isEmpty()) {
            result.add("Diagram has been started — a great first step!");
        }
        return result;
    }

    private List<String> computeImprovements(SysdesignProblem problem) {
        List<String> improvements = new ArrayList<>();
        improvements.add("Consider adding a load balancer or API Gateway in front of your services");
        improvements.add("Think about data consistency — identify which parts of the system require strong vs. eventual consistency");

        // Add requirement-specific suggestion if present
        if (problem.getRequirements() != null && !problem.getRequirements().isEmpty()) {
            String firstReq = problem.getRequirements().get(0);
            improvements.add("Ensure your design addresses: \"" + firstReq + "\"");
        }
        return improvements;
    }

    private List<FeedbackRecord.ComponentFeedback> computeComponentFeedback(DiagramContent diagram) {
        List<FeedbackRecord.ComponentFeedback> result = new ArrayList<>();
        if (diagram.getNodes() == null || diagram.getNodes().isEmpty()) {
            result.add(new FeedbackRecord.ComponentFeedback(
                "Overall",
                "No components found. Start by adding key system components like Client, API Gateway, and Databases.",
                "ERROR"
            ));
            return result;
        }

        for (DiagramContent.DiagramNode node : diagram.getNodes()) {
            String type = node.type() != null ? node.type().toLowerCase() : "";
            String feedback;
            String severity;

            if (type.contains("database") || type.contains("db") || type.contains("storage")) {
                feedback = "Good — databases are core components. Consider specifying SQL vs. NoSQL trade-offs.";
                severity = "INFO";
            } else if (type.contains("cache") || type.contains("redis")) {
                feedback = "Excellent choice for caching. Make sure to define eviction policy and TTL.";
                severity = "INFO";
            } else if (type.contains("queue") || type.contains("kafka") || type.contains("mq")) {
                feedback = "Message queues improve decoupling. Specify delivery guarantees (at-least-once vs. exactly-once).";
                severity = "INFO";
            } else if (type.contains("load") || type.contains("gateway") || type.contains("proxy")) {
                feedback = "Good use of a traffic management layer. Define routing and health-check strategy.";
                severity = "INFO";
            } else if (type.contains("service") || type.contains("api")) {
                feedback = "Service identified. Consider defining its responsibilities and API contract.";
                severity = "INFO";
            } else {
                feedback = "Component role is not immediately clear — consider labeling or annotating it.";
                severity = "WARNING";
            }

            result.add(new FeedbackRecord.ComponentFeedback(
                node.label() != null ? node.label() : node.id(),
                feedback,
                severity
            ));
        }
        return result;
    }

    private String buildSummary(int score, int nodeCount, String problemTitle) {
        if (score < 40) {
            return "Your solution for \"" + problemTitle + "\" is in early stages. "
                + "Start by identifying the key components such as clients, servers, and data stores.";
        } else if (score < 65) {
            return "Your solution shows initial thinking but needs more depth. "
                + "You have " + nodeCount + " component(s) — expand to cover the full request flow.";
        } else if (score < 80) {
            return "Good progress on \"" + problemTitle + "\"! You've covered the essential components. "
                + "Now focus on scalability and fault tolerance mechanisms.";
        } else if (score < 90) {
            return "Strong solution for \"" + problemTitle + "\"! Your diagram demonstrates solid system "
                + "design thinking. Consider edge cases and failure scenarios.";
        } else {
            return "Excellent solution for \"" + problemTitle + "\"! Comprehensive coverage of components, "
                + "data flows, and architectural patterns. Well done!";
        }
    }

    private DiagramFeedback toDto(FeedbackRecord r) {
        return new DiagramFeedback(
            r.getId(),
            r.getDiagramId(),
            r.getOverallScore(),
            r.getSummary(),
            r.getStrengths(),
            r.getImprovements(),
            r.getComponentFeedback(),
            r.getGeneratedAt()
        );
    }
}
