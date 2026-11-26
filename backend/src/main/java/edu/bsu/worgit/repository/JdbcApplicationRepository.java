package edu.bsu.worgit.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.ApplicationStatus;
import edu.bsu.worgit.domain.Rows;

@Repository
public class JdbcApplicationRepository implements ApplicationRepository {

    private static final String SELECT = """
            SELECT a.application_id, a.student_id, u.full_name, a.org_id, o.name AS org_name,
                   a.date_applied, a.status
              FROM membership_applications a
              JOIN users u ON u.user_id = a.student_id
              JOIN organizations o ON o.org_id = a.org_id
            """;

    private final JdbcClient jdbc;

    public JdbcApplicationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Rows.Application map(ResultSet rs) throws SQLException {
        return new Rows.Application(
                rs.getObject("application_id", UUID.class),
                rs.getString("student_id"),
                rs.getString("full_name"),
                rs.getObject("org_id", UUID.class),
                rs.getString("org_name"),
                rs.getObject("date_applied", LocalDate.class),
                ApplicationStatus.valueOf(rs.getString("status")));
    }

    @Override
    public boolean hasPendingApplication(String studentId, UUID orgId) {
        Boolean found = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM membership_applications
                                WHERE student_id = :student AND org_id = :org AND status = 'PENDING')
                """)
                .param("student", studentId)
                .param("org", orgId)
                .query(Boolean.class).single();
        return Boolean.TRUE.equals(found);
    }

    @Override
    public UUID insertApplication(String studentId, UUID orgId, LocalDate dateApplied) {
        return jdbc.sql("""
                INSERT INTO membership_applications (student_id, org_id, date_applied)
                VALUES (:student, :org, :date)
                RETURNING application_id
                """)
                .param("student", studentId)
                .param("org", orgId)
                .param("date", dateApplied)
                .query((rs, n) -> rs.getObject("application_id", UUID.class))
                .single();
    }

    @Override
    public Optional<Rows.Application> findApplication(UUID applicationId) {
        return jdbc.sql(SELECT + " WHERE a.application_id = :id")
                .param("id", applicationId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public boolean decideApplication(UUID applicationId, ApplicationStatus newStatus) {
        return jdbc.sql("UPDATE membership_applications SET status = :status WHERE application_id = :id AND status = 'PENDING'")
                .param("status", newStatus.name())
                .param("id", applicationId)
                .update() > 0;
    }

    @Override
    public List<Rows.Application> listApplications(ApplicationStatus status, Set<UUID> onlyOrgIds, UUID orgFilter) {
        if (onlyOrgIds != null && onlyOrgIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        appendScope(sql, params, onlyOrgIds, orgFilter);
        if (status != null) {
            sql.append(" AND a.status = :status");
            params.put("status", status.name());
        }
        sql.append(" ORDER BY a.date_applied DESC, u.full_name");
        return jdbc.sql(sql.toString()).params(params).query((rs, n) -> map(rs)).list();
    }

    @Override
    public Map<ApplicationStatus, Integer> countApplications(Set<UUID> onlyOrgIds, UUID orgFilter) {
        Map<ApplicationStatus, Integer> counts = new EnumMap<>(ApplicationStatus.class);
        if (onlyOrgIds != null && onlyOrgIds.isEmpty()) {
            return counts;
        }
        StringBuilder sql = new StringBuilder("SELECT a.status, count(*) AS n FROM membership_applications a WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        appendScope(sql, params, onlyOrgIds, orgFilter);
        sql.append(" GROUP BY a.status");
        jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> Map.entry(ApplicationStatus.valueOf(rs.getString("status")), rs.getInt("n")))
                .list()
                .forEach(e -> counts.put(e.getKey(), e.getValue()));
        return counts;
    }

    @Override
    public List<Rows.Application> listApplicationsOf(String studentId) {
        return jdbc.sql(SELECT + " WHERE a.student_id = :student ORDER BY a.date_applied DESC, o.name")
                .param("student", studentId)
                .query((rs, n) -> map(rs))
                .list();
    }

    private static void appendScope(StringBuilder sql, Map<String, Object> params, Set<UUID> onlyOrgIds, UUID orgFilter) {
        if (onlyOrgIds != null) {
            sql.append(" AND a.org_id IN (:scope)");
            params.put("scope", onlyOrgIds);
        }
        if (orgFilter != null) {
            sql.append(" AND a.org_id = :orgFilter");
            params.put("orgFilter", orgFilter);
        }
    }
}
