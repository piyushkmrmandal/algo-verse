package com.algoverse.sysdesign.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "diagram_content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiagramContent {

    @Id
    private String id;  // same as UserDiagram.id (UUID string)

    private String diagramId;

    private List<DiagramNode> nodes;

    private List<DiagramEdge> edges;

    private Map<String, Object> metadata;  // zoom level, viewport, etc.

    private Instant savedAt;

    public record DiagramNode(
        String id,
        String type,
        String label,
        double x,
        double y,
        Map<String, Object> props
    ) {}

    public record DiagramEdge(
        String id,
        String source,
        String target,
        String label,
        String edgeType
    ) {}
}
