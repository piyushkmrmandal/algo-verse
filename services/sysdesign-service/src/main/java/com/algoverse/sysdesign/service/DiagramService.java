package com.algoverse.sysdesign.service;

import com.algoverse.sysdesign.domain.DiagramContent;
import com.algoverse.sysdesign.domain.UserDiagram;
import com.algoverse.sysdesign.dto.DiagramDto;
import com.algoverse.sysdesign.dto.SaveDiagramRequest;
import com.algoverse.sysdesign.repository.DiagramContentRepository;
import com.algoverse.sysdesign.repository.UserDiagramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagramService {

    private final UserDiagramRepository userDiagramRepository;
    private final DiagramContentRepository diagramContentRepository;

    @Transactional
    public DiagramDto saveDiagram(UUID userId, SaveDiagramRequest request) {
        // Upsert UserDiagram in Postgres
        UserDiagram diagram = userDiagramRepository
            .findByUserIdAndProblemId(userId, request.problemId())
            .orElseGet(() -> UserDiagram.builder()
                .userId(userId)
                .problemId(request.problemId())
                .build());

        if (diagram.getId() != null) {
            // Existing diagram — increment version
            diagram.setVersion(diagram.getVersion() + 1);
        }

        if (request.title() != null && !request.title().isBlank()) {
            diagram.setTitle(request.title());
        }

        UserDiagram saved = userDiagramRepository.save(diagram);

        // Save DiagramContent to MongoDB
        String diagramIdStr = saved.getId().toString();
        DiagramContent content = diagramContentRepository.findByDiagramId(diagramIdStr)
            .orElseGet(() -> DiagramContent.builder()
                .diagramId(diagramIdStr)
                .build());

        content.setNodes(request.nodes());
        content.setEdges(request.edges());
        content.setMetadata(request.metadata());
        content.setSavedAt(Instant.now());

        diagramContentRepository.save(content);

        log.debug("Saved diagram {} for user {} (version {})", saved.getId(), userId, saved.getVersion());
        return toDto(saved, content);
    }

    public DiagramDto getDiagram(UUID diagramId, UUID userId) {
        UserDiagram diagram = userDiagramRepository.findById(diagramId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram not found"));

        if (!diagram.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        DiagramContent content = diagramContentRepository
            .findByDiagramId(diagramId.toString())
            .orElse(null);

        return toDto(diagram, content);
    }

    public List<DiagramDto> getUserDiagrams(UUID userId) {
        return userDiagramRepository.findByUserId(userId)
            .stream()
            .map(d -> toDto(d, null))
            .toList();
    }

    @Transactional
    public void deleteDiagram(UUID diagramId, UUID userId) {
        UserDiagram diagram = userDiagramRepository.findById(diagramId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram not found"));

        if (!diagram.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        diagramContentRepository.deleteByDiagramId(diagramId.toString());
        userDiagramRepository.deleteById(diagramId);

        log.debug("Deleted diagram {} for user {}", diagramId, userId);
    }

    private DiagramDto toDto(UserDiagram d, DiagramContent c) {
        return new DiagramDto(
            d.getId(),
            d.getUserId(),
            d.getProblemId(),
            d.getTitle(),
            d.getVersion(),
            d.isSubmitted(),
            d.getCreatedAt(),
            d.getUpdatedAt(),
            c != null ? c.getNodes() : null,
            c != null ? c.getEdges() : null,
            c != null ? c.getMetadata() : null
        );
    }
}
