package com.algoverse.sysdesign.repository;

import com.algoverse.sysdesign.domain.DiagramContent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiagramContentRepository extends MongoRepository<DiagramContent, String> {

    Optional<DiagramContent> findByDiagramId(String diagramId);

    void deleteByDiagramId(String diagramId);
}
