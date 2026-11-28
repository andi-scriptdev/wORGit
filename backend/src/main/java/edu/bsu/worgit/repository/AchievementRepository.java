package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.List;

import edu.bsu.worgit.domain.EventRows;

public interface AchievementRepository {

    List<EventRows.EarnedAchievement> achievementsOf(String studentId);

    /** True when newly earned; false when the student already had it. */
    boolean insertAchievement(String studentId, String title, LocalDate dateEarned);
}
