package com.algoverse.gamification.service;

import com.algoverse.gamification.domain.UserXp;
import com.algoverse.gamification.dto.XpResponse;
import com.algoverse.gamification.repository.UserXpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link XpService}.
 *
 * <p>Verifies XP award amounts per difficulty, level computation formula,
 * XP-to-next-level calculation, and streak bonus logic.
 */
@ExtendWith(MockitoExtension.class)
class XpServiceTest {

    @Mock
    private UserXpRepository userXpRepository;

    @Mock
    private LeaderboardService leaderboardService;

    @InjectMocks
    private XpService xpService;

    private static final UUID USER_ID = UUID.randomUUID();

    // -----------------------------------------------------------------------
    // Static helpers — no mocks needed
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("XP per difficulty (static helper)")
    class XpPerDifficulty {

        @Test
        @DisplayName("EASY first-solve awards 50 XP")
        void easyFirstSolveAwards50Xp() {
            assertThat(XpService.xpForDifficulty("EASY")).isEqualTo(50);
        }

        @Test
        @DisplayName("MEDIUM first-solve awards 100 XP")
        void mediumFirstSolveAwards100Xp() {
            assertThat(XpService.xpForDifficulty("MEDIUM")).isEqualTo(100);
        }

        @Test
        @DisplayName("HARD first-solve awards 200 XP")
        void hardFirstSolveAwards200Xp() {
            assertThat(XpService.xpForDifficulty("HARD")).isEqualTo(200);
        }

        @Test
        @DisplayName("Unknown difficulty defaults to EASY XP (50)")
        void unknownDifficultyDefaultsToEasy() {
            assertThat(XpService.xpForDifficulty("UNKNOWN")).isEqualTo(50);
        }
    }

    // -----------------------------------------------------------------------
    // Level computation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Level computation: level = floor(sqrt(xp / 100)) + 1")
    class LevelComputation {

        @Test
        @DisplayName("0 XP → level 1")
        void zeroXpIsLevel1() {
            assertThat(XpService.computeLevel(0)).isEqualTo(1);
        }

        @Test
        @DisplayName("99 XP → level 1")
        void ninetyNineXpIsLevel1() {
            assertThat(XpService.computeLevel(99)).isEqualTo(1);
        }

        @Test
        @DisplayName("100 XP → level 2")
        void oneHundredXpIsLevel2() {
            assertThat(XpService.computeLevel(100)).isEqualTo(2);
        }

        @Test
        @DisplayName("400 XP → level 3")
        void fourHundredXpIsLevel3() {
            assertThat(XpService.computeLevel(400)).isEqualTo(3);
        }

        @Test
        @DisplayName("900 XP → level 4")
        void nineHundredXpIsLevel4() {
            assertThat(XpService.computeLevel(900)).isEqualTo(4);
        }

        @Test
        @DisplayName("1000 XP → level 4 (just below level 5 threshold)")
        void oneThousandXpIsLevel4() {
            // floor(sqrt(1000/100)) + 1 = floor(sqrt(10)) + 1 = floor(3.16) + 1 = 4
            assertThat(XpService.computeLevel(1000)).isEqualTo(4);
        }

        @Test
        @DisplayName("2500 XP → level 6")
        void twoThousandFiveHundredXpIsLevel6() {
            // floor(sqrt(2500/100)) + 1 = floor(5) + 1 = 6
            assertThat(XpService.computeLevel(2500)).isEqualTo(6);
        }
    }

    // -----------------------------------------------------------------------
    // XP to next level
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("XP to next level")
    class XpToNextLevel {

        @Test
        @DisplayName("At 0 XP, need 100 XP to reach level 2")
        void zeroXpNeeds100ToNextLevel() {
            assertThat(XpService.xpToNextLevel(0)).isEqualTo(100);
        }

        @Test
        @DisplayName("At 50 XP (level 1), need 50 more XP to reach level 2")
        void fiftyXpNeeds50More() {
            assertThat(XpService.xpToNextLevel(50)).isEqualTo(50);
        }

        @Test
        @DisplayName("At 100 XP (level 2), need 300 more XP to reach level 3 (threshold = 400)")
        void hundredXpNeedsCorrectAmount() {
            // Next level (3) threshold = (3-1)^2 * 100 = 4 * 100 = 400
            assertThat(XpService.xpToNextLevel(100)).isEqualTo(300);
        }
    }

    // -----------------------------------------------------------------------
    // awardForSolve integration (with mocked repository)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("awardForSolve")
    class AwardForSolve {

        @BeforeEach
        void setUp() {
            UserXp existingXp = new UserXp(USER_ID);
            when(userXpRepository.findById(USER_ID)).thenReturn(Optional.of(existingXp));
            when(userXpRepository.save(any(UserXp.class))).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(leaderboardService).updateScore(any(), anyInt(), any());
        }

        @Test
        @DisplayName("First-solve EASY awards 50 XP")
        void firstSolveEasyAwards50Xp() {
            XpResponse response = xpService.awardForSolve(USER_ID, "EASY", true);
            assertThat(response.totalXp()).isEqualTo(50);
            assertThat(response.level()).isEqualTo(1);
        }

        @Test
        @DisplayName("First-solve MEDIUM awards 100 XP")
        void firstSolveMediumAwards100Xp() {
            XpResponse response = xpService.awardForSolve(USER_ID, "MEDIUM", true);
            assertThat(response.totalXp()).isEqualTo(100);
            assertThat(response.level()).isEqualTo(2);
        }

        @Test
        @DisplayName("First-solve HARD awards 200 XP")
        void firstSolveHardAwards200Xp() {
            XpResponse response = xpService.awardForSolve(USER_ID, "HARD", true);
            assertThat(response.totalXp()).isEqualTo(200);
        }

        @Test
        @DisplayName("Non-first-solve does not award XP but increments solve count")
        void nonFirstSolveDoesNotAwardXp() {
            XpResponse response = xpService.awardForSolve(USER_ID, "HARD", false);
            assertThat(response.totalXp()).isEqualTo(0);
            assertThat(response.hardSolves()).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // awardStreakBonus
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("awardStreakBonus")
    class AwardStreakBonus {

        @BeforeEach
        void setUp() {
            UserXp existingXp = new UserXp(USER_ID);
            when(userXpRepository.findById(USER_ID)).thenReturn(Optional.of(existingXp));
            when(userXpRepository.save(any(UserXp.class))).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(leaderboardService).updateScore(any(), anyInt(), any());
        }

        @Test
        @DisplayName("Regular streak day awards base bonus of 25 XP")
        void regularStreakDayAwards25Xp() {
            XpResponse response = xpService.awardStreakBonus(USER_ID, 3);
            assertThat(response.totalXp()).isEqualTo(XpService.XP_DAILY_STREAK_BONUS);
        }

        @Test
        @DisplayName("7-day milestone awards 25 + 150 = 175 XP")
        void sevenDayMilestoneAwards175Xp() {
            XpResponse response = xpService.awardStreakBonus(USER_ID, 7);
            assertThat(response.totalXp())
                    .isEqualTo(XpService.XP_DAILY_STREAK_BONUS + XpService.XP_7_DAY_MILESTONE);
        }

        @Test
        @DisplayName("30-day milestone awards 25 + 500 = 525 XP")
        void thirtyDayMilestoneAwards525Xp() {
            XpResponse response = xpService.awardStreakBonus(USER_ID, 30);
            assertThat(response.totalXp())
                    .isEqualTo(XpService.XP_DAILY_STREAK_BONUS + XpService.XP_30_DAY_MILESTONE);
        }
    }
}
