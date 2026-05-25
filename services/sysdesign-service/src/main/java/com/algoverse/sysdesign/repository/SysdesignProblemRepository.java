package com.algoverse.sysdesign.repository;

import com.algoverse.sysdesign.domain.SysdesignProblem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SysdesignProblemRepository extends JpaRepository<SysdesignProblem, UUID> {

    Optional<SysdesignProblem> findBySlug(String slug);

    Page<SysdesignProblem> findByPublishedTrue(Pageable pageable);

    Page<SysdesignProblem> findByCategoryAndPublishedTrue(String category, Pageable pageable);
}
