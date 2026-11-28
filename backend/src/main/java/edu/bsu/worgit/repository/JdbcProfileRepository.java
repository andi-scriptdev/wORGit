package edu.bsu.worgit.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.PeopleRows;

@Repository
public class JdbcProfileRepository implements ProfileRepository {

    private final JdbcClient jdbc;

    public JdbcProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PeopleRows.StudentProfile> findStudentProfile(String studentId) {
        Optional<PeopleRows.StudentProfile> base = jdbc.sql("""
                SELECT u.user_id, u.full_name, u.display_name, u.email, s.bio, s.visible_in_matching
                  FROM students s
                  JOIN users u ON u.user_id = s.user_id
                 WHERE s.user_id = :id
                """)
                .param("id", studentId)
                .query((rs, n) -> new PeopleRows.StudentProfile(
                        rs.getString("user_id"),
                        rs.getString("full_name"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("bio"),
                        rs.getBoolean("visible_in_matching"),
                        List.of()))
                .optional();
        if (base.isEmpty()) {
            return Optional.empty();
        }
        List<String> interests = jdbc.sql("SELECT interest FROM student_interests WHERE user_id = :id ORDER BY interest")
                .param("id", studentId)
                .query((rs, n) -> rs.getString("interest"))
                .list();
        PeopleRows.StudentProfile p = base.get();
        return Optional.of(new PeopleRows.StudentProfile(p.userId(), p.fullName(), p.displayName(), p.email(),
                p.bio(), p.visibleInMatching(), interests));
    }

    @Override
    public void updateStudentProfile(String studentId, String displayName, String bio, boolean visibleInMatching) {
        updateDisplayName(studentId, displayName);
        jdbc.sql("UPDATE students SET bio = :bio, visible_in_matching = :visible WHERE user_id = :id")
                .param("bio", bio)
                .param("visible", visibleInMatching)
                .param("id", studentId)
                .update();
    }

    @Override
    public void replaceStudentInterests(String studentId, Set<String> interests) {
        jdbc.sql("DELETE FROM student_interests WHERE user_id = :id").param("id", studentId).update();
        for (String interest : interests) {
            jdbc.sql("INSERT INTO student_interests (user_id, interest) VALUES (:id, :interest)")
                    .param("id", studentId)
                    .param("interest", interest)
                    .update();
        }
    }

    @Override
    public void updateDisplayName(String userId, String displayName) {
        jdbc.sql("UPDATE users SET display_name = :name WHERE user_id = :id")
                .param("name", displayName)
                .param("id", userId)
                .update();
    }

    @Override
    public void updatePasswordHash(String userId, String passwordHash) {
        jdbc.sql("UPDATE users SET password_hash = :hash WHERE user_id = :id")
                .param("hash", passwordHash)
                .param("id", userId)
                .update();
    }
}
