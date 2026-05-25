package com.algoverse.sysdesign.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "diagram_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackRecord {

    @Id
    private String id;

    private String diagramId;

    private String userId;

    private String problemSlug;

    private int overallScore;  // 0-100

    private String summary;

    private List<String> strengths;

    private List<String> improvements;

    private List<ComponentFeedback> componentFeedback;

    private Instant generatedAt;

    public record ComponentFeedback(
        String component,
        String feedback,
        String severity  // INFO, WARNING, ERROR
    ) {}
}
