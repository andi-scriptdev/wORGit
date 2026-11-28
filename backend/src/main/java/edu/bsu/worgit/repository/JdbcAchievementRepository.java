package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.EventRows;

@Repository
public class JdbcAchievementRepository implements AchievementRepository {

    private final JdbcClient jdbc;

    public JdbcAchievementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EventRows.EarnedAchievement> achievementsOf(String studentId) {
        return jdbc.sql("SELECT title, date_earned FROM achievements WHERE student_id = :student ORDER BY date_earned, title")
                .param("student", studentId)
                .query((rs, n) -> new EventRows.EarnedAchievement(
                        rs.getString("title"), rs.getObject("date_earned", LocalDate.class)))
                .list();
    }

    @Override
    public boolean insertAchievement(String studentId, String title, LocalDate dateEarned) {
        return jdbc.sql("""
                INSERT INTO achievements (student_id, title, date_earned) VALUES (:student, :title, :date)
                ON CONFLICT (student_id, title) DO NOTHING
                """)
                .param("student", studentId)
                .param("title", title)
                .param("date", dateEarned)
                .update() > 0;
    }
}
