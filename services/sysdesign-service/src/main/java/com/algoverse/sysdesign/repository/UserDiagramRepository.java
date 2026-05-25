package com.algoverse.sysdesign.repository;

import com.algoverse.sysdesign.domain.UserDiagram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDiagramRepository extends JpaRepository<UserDiagram, UUID> {

    List<UserDiagram> findByUserId(UUID userId);

    Optional<UserDiagram> findByUserIdAndProblemId(UUID userId, UUID problemId);
}
