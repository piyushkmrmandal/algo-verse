package com.algoverse.problem.infrastructure.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProblemDocumentRepository extends MongoRepository<ProblemDocument, String> {

    Optional<ProblemDocument> findBySlug(String slug);

    boolean existsBySlug(String slug);

    void deleteBySlug(String slug);
}
