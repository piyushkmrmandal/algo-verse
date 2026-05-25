package com.algoverse.problem.infrastructure.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProblemSearchRepository extends ElasticsearchRepository<ProblemSearchDoc, String> {

    List<ProblemSearchDoc> findByTitleContainingIgnoreCase(String title);

    List<ProblemSearchDoc> findByDifficulty(String difficulty);

    List<ProblemSearchDoc> findByTopicSlugsContaining(String topicSlug);
}
