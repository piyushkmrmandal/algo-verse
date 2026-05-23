package com.algoverse.problem.domain.repository;

import com.algoverse.problem.domain.model.ProblemTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProblemTopicRepository extends JpaRepository<ProblemTopic, UUID> {

    List<ProblemTopic> findByProblemId(UUID problemId);

    List<ProblemTopic> findByTopicId(UUID topicId);

    void deleteByProblemId(UUID problemId);
}
