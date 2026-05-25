package com.algoverse.sysdesign.dto;

import com.algoverse.sysdesign.domain.FeedbackRecord;

import java.time.Instant;
import java.util.List;

public record DiagramFeedback(
    String feedbackId,
    String diagramId,
    int overallScore,
    String summary,
    List<String> strengths,
    List<String> improvements,
    List<FeedbackRecord.ComponentFeedback> componentFeedback,
    Instant generatedAt
) {}
