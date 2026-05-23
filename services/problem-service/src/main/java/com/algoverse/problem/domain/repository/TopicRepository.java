package com.algoverse.problem.domain.repository;

import com.algoverse.problem.domain.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findBySlug(String slug);

    List<Topic> findAll();
}
