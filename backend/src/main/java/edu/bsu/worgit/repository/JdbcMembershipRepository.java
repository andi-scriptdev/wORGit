package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.Rows;

@Repository
public class JdbcMembershipRepository implements MembershipRepository {

    private final JdbcClient jdbc;

    public JdbcMembershipRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isMember(String studentId, UUID orgId) {
        Boolean found = jdbc.sql("SELECT EXISTS (SELECT 1 FROM org_members WHERE org_id = :org AND student_id = :student)")
                .param("org", orgId)
                .param("student", studentId)
                .query(Boolean.class).single();
        return Boolean.TRUE.equals(found);
    }

    @Override
    public Set<UUID> memberOrgIds(String studentId) {
        return new HashSet<>(jdbc.sql("SELECT org_id FROM org_members WHERE student_id = :student")
                .param("student", studentId)
                .query((rs, n) -> rs.getObject("org_id", UUID.class))
                .list());
    }

    @Override
    public void addMember(UUID orgId, String studentId, LocalDate joinedOn) {
        jdbc.sql("INSERT INTO org_members (org_id, student_id, joined_on) VALUES (:org, :student, :joined)")
                .param("org", orgId)
                .param("student", studentId)
                .param("joined", joinedOn)
                .update();
    }

    @Override
    public boolean removeMember(UUID orgId, String studentId) {
        return jdbc.sql("DELETE FROM org_members WHERE org_id = :org AND student_id = :student")
                .param("org", orgId)
                .param("student", studentId)
                .update() > 0;
    }

    @Override
    public List<Rows.Member> listMembers(UUID orgId, String search) {
        StringBuilder sql = new StringBuilder("""
                SELECT m.student_id, u.full_name, m.joined_on
                  FROM org_members m
                  JOIN users u ON u.user_id = m.student_id
                 WHERE m.org_id = :org
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("org", orgId);
        if (search != null) {
            sql.append(" AND (lower(u.full_name) LIKE :q OR lower(m.student_id) LIKE :q)");
            params.put("q", SqlUtil.likePattern(search));
        }
        sql.append(" ORDER BY u.full_name, m.student_id");
        return jdbc.sql(sql.toString())
                .params(params)
                .query((rs, n) -> new Rows.Member(
                        rs.getString("student_id"),
                        rs.getString("full_name"),
                        rs.getObject("joined_on", LocalDate.class)))
                .list();
    }
}
