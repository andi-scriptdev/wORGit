package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.EventRows;

@Repository
public class JdbcStatsRepository implements StatsRepository {

    private final JdbcClient jdbc;

    public JdbcStatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EventRows.StudentStats studentStats(String studentId, LocalDate upTo) {
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM org_members WHERE student_id = :s) AS orgs,
                       (SELECT count(*) FROM chat_messages WHERE sender_id = :s) AS messages,
                       (SELECT count(*) FROM peer_connections WHERE :s IN (student_a_id, student_b_id)) AS peers,
                       (SELECT count(*) FROM event_participations p JOIN events e ON e.event_id = p.event_id
                         WHERE p.student_id = :s AND e.event_date <= :upTo) AS joined,
                       (SELECT count(*) FROM event_participations p JOIN events e ON e.event_id = p.event_id
                         WHERE p.student_id = :s AND e.event_date <= :upTo AND p.attended) AS attended
                """)
                .param("s", studentId)
                .param("upTo", upTo)
                .query((rs, n) -> new EventRows.StudentStats(rs.getInt("orgs"), rs.getInt("messages"),
                        rs.getInt("peers"), rs.getInt("joined"), rs.getInt("attended")))
                .single();
    }

    @Override
    public EventRows.CampusTotals campusTotals() {
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM students) AS students,
                       (SELECT count(*) FROM organizations) AS organizations,
                       (SELECT count(*) FROM chat_messages) AS messages,
                       (SELECT count(*) FROM peer_connections) AS connections
                """)
                .query((rs, n) -> new EventRows.CampusTotals(rs.getInt("students"), rs.getInt("organizations"),
                        rs.getInt("messages"), rs.getInt("connections")))
                .single();
    }

    @Override
    public List<EventRows.OrgCount> membersPerOrganization(Set<UUID> scope) {
        if (scope != null && scope.isEmpty()) {
            return List.of();
        }
        Map<String, Object> params = new HashMap<>();
        String where = "";
        if (scope != null) {
            where = " WHERE o.org_id IN (:scope)";
            params.put("scope", scope);
        }
        return jdbc.sql("""
                SELECT o.org_id, o.name, count(m.student_id) AS members
                  FROM organizations o
                  LEFT JOIN org_members m ON m.org_id = o.org_id
                """ + where + " GROUP BY o.org_id, o.name ORDER BY count(m.student_id) DESC, o.name")
                .params(params)
                .query((rs, n) -> new EventRows.OrgCount(rs.getObject("org_id", UUID.class), rs.getString("name"),
                        rs.getInt("members")))
                .list();
    }

    @Override
    public int pendingApplications(Set<UUID> scope) {
        return count("SELECT count(*) FROM membership_applications WHERE status = 'PENDING'", scope, Map.of());
    }

    @Override
    public int unpaidFees(Set<UUID> scope) {
        return count("SELECT count(*) FROM payment_records WHERE status = 'UNPAID'", scope, Map.of());
    }

    @Override
    public int eventsBetween(Set<UUID> scope, LocalDate from, LocalDate to) {
        return count("SELECT count(*) FROM events WHERE event_date BETWEEN :from AND :to", scope,
                Map.of("from", from, "to", to));
    }

    /** Appends "AND org_id IN (scope)"; scope null = everything, empty = 0. */
    private int count(String sql, Set<UUID> scope, Map<String, Object> extra) {
        if (scope != null && scope.isEmpty()) {
            return 0;
        }
        Map<String, Object> params = new HashMap<>(extra);
        String full = sql;
        if (scope != null) {
            full += " AND org_id IN (:scope)";
            params.put("scope", scope);
        }
        Integer n = jdbc.sql(full).params(params).query(Integer.class).single();
        return n == null ? 0 : n;
    }

    @Override
    public List<EventRows.StudentRef> searchStudents(String text, Set<UUID> scope, int limit) {
        if (scope != null && scope.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT u.user_id, u.full_name, u.display_name
                  FROM students s
                  JOIN users u ON u.user_id = s.user_id
                 WHERE (lower(u.full_name) LIKE :q OR lower(u.display_name) LIKE :q OR lower(u.user_id) LIKE :q)
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("q", SqlUtil.likePattern(text));
        params.put("limit", limit);
        if (scope != null) {
            sql.append(" AND s.user_id IN (SELECT student_id FROM org_members WHERE org_id IN (:scope))");
            params.put("scope", scope);
        }
        sql.append(" ORDER BY u.full_name, u.user_id LIMIT :limit");
        return jdbc.sql(sql.toString())
                .params(params)
                .query((rs, n) -> new EventRows.StudentRef(rs.getString("user_id"), rs.getString("full_name"),
                        rs.getString("display_name")))
                .list();
    }

    @Override
    public boolean isMemberOfAny(String studentId, Set<UUID> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return false;
        }
        Boolean found = jdbc.sql("SELECT EXISTS (SELECT 1 FROM org_members WHERE student_id = :s AND org_id IN (:orgs))")
                .param("s", studentId)
                .param("orgs", orgIds)
                .query(Boolean.class).single();
        return Boolean.TRUE.equals(found);
    }
}
