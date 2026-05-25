package com.algoverse.gamification.repository;

import com.algoverse.gamification.domain.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    Optional<Badge> findBySlug(String slug);
}
