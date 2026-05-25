package com.algoverse.gamification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AlgoVerse Gamification Service entry point.
 *
 * <p>Handles XP awarding, daily streak tracking, badge evaluation, and leaderboard
 * management for users who solve DSA problems on the AlgoVerse platform.
 */
@SpringBootApplication
@EnableScheduling
public class GamificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(GamificationApplication.class, args);
    }
}
