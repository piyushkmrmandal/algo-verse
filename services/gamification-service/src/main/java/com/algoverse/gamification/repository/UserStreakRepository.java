package com.algoverse.gamification.repository;

import com.algoverse.gamification.domain.UserStreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserStreakRepository extends JpaRepository<UserStreak, UUID> {
}
