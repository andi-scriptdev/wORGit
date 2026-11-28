package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.CertificateType;
import edu.bsu.worgit.domain.EventRole;
import edu.bsu.worgit.domain.EventRows;

@Repository
public class JdbcEventAdminRepository implements EventAdminRepository {

    private final JdbcClient jdbc;

    public JdbcEventAdminRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EventRows.EventListRow> listEvents(Set<UUID> scope, UUID orgFilter) {
        if (scope != null && scope.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT e.event_id, e.title, e.org_id, o.name AS org_name, e.event_date,
                       (SELECT count(*) FROM event_participations p WHERE p.event_id = e.event_id) AS people,
                       (SELECT count(*) FROM certificates c WHERE c.event_id = e.event_id) AS certificates
                  FROM events e
                  JOIN organizations o ON o.org_id = e.org_id
                 WHERE 1 = 1
                """);
        Map<String, Object> params = new HashMap<>();
        if (scope != null) {
            sql.append(" AND e.org_id IN (:scope)");
            params.put("scope", scope);
        }
        if (orgFilter != null) {
            sql.append(" AND e.org_id = :org");
            params.put("org", orgFilter);
        }
        sql.append(" ORDER BY e.event_date DESC, e.title");
        return jdbc.sql(sql.toString())
                .params(params)
                .query((rs, n) -> new EventRows.EventListRow(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("org_id", UUID.class),
                        rs.getString("org_name"),
                        rs.getObject("event_date", LocalDate.class),
                        rs.getInt("people"),
                        rs.getInt("certificates")))
                .list();
    }

    @Override
    public Optional<EventRows.EventInfo> findEvent(UUID eventId) {
        return jdbc.sql("""
                SELECT e.event_id, e.title, e.org_id, o.name AS org_name, e.event_date, e.description
                  FROM events e
                  JOIN organizations o ON o.org_id = e.org_id
                 WHERE e.event_id = :id
                """)
                .param("id", eventId)
                .query((rs, n) -> new EventRows.EventInfo(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("org_id", UUID.class),
                        rs.getString("org_name"),
                        rs.getObject("event_date", LocalDate.class),
                        rs.getString("description")))
                .optional();
    }

    @Override
    public UUID insertEvent(UUID orgId, String title, LocalDate eventDate, String description) {
        return jdbc.sql("""
                INSERT INTO events (org_id, title, event_date, description)
                VALUES (:org, :title, :date, :description)
                RETURNING event_id
                """)
                .param("org", orgId)
                .param("title", title)
                .param("date", eventDate)
                .param("description", description)
                .query((rs, n) -> rs.getObject("event_id", UUID.class))
                .single();
    }

    @Override
    public void deleteEvent(UUID eventId) {
        jdbc.sql("DELETE FROM events WHERE event_id = :id").param("id", eventId).update();
    }

    @Override
    public List<EventRows.Participant> participants(UUID eventId) {
        return jdbc.sql("""
                SELECT p.student_id, u.full_name, p.role, p.attended, c.certificate_id, c.certificate_type
                  FROM event_participations p
                  JOIN users u ON u.user_id = p.student_id
                  LEFT JOIN certificates c ON c.event_id = p.event_id AND c.student_id = p.student_id
                 WHERE p.event_id = :id
                 ORDER BY u.full_name, p.student_id
                """)
                .param("id", eventId)
                .query((rs, n) -> {
                    String type = rs.getString("certificate_type");
                    return new EventRows.Participant(
                            rs.getString("student_id"),
                            rs.getString("full_name"),
                            EventRole.valueOf(rs.getString("role")),
                            rs.getBoolean("attended"),
                            rs.getString("certificate_id"),
                            type == null ? null : CertificateType.valueOf(type));
                })
                .list();
    }

    @Override
    public void addParticipant(UUID eventId, String studentId, EventRole role) {
        jdbc.sql("INSERT INTO event_participations (event_id, student_id, role) VALUES (:event, :student, :role)")
                .param("event", eventId)
                .param("student", studentId)
                .param("role", role.name())
                .update();
    }

    @Override
    public boolean removeParticipant(UUID eventId, String studentId) {
        return jdbc.sql("DELETE FROM event_participations WHERE event_id = :event AND student_id = :student")
                .param("event", eventId)
                .param("student", studentId)
                .update() > 0;
    }

    @Override
    public void setAttended(UUID eventId, String studentId, boolean attended) {
        jdbc.sql("UPDATE event_participations SET attended = :attended WHERE event_id = :event AND student_id = :student")
                .param("attended", attended)
                .param("event", eventId)
                .param("student", studentId)
                .update();
    }

    @Override
    public int attendedEventCount(String studentId) {
        Integer n = jdbc.sql("SELECT count(*) FROM event_participations WHERE student_id = :student AND attended")
                .param("student", studentId)
                .query(Integer.class).single();
        return n == null ? 0 : n;
    }

    @Override
    public List<EventRows.JoinedEvent> joinedEvents(String studentId) {
        return jdbc.sql("""
                SELECT e.event_id, e.title, o.name AS org_name, e.event_date, p.role, p.attended
                  FROM event_participations p
                  JOIN events e ON e.event_id = p.event_id
                  JOIN organizations o ON o.org_id = e.org_id
                 WHERE p.student_id = :student
                 ORDER BY e.event_date DESC, e.title
                """)
                .param("student", studentId)
                .query((rs, n) -> new EventRows.JoinedEvent(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("title"),
                        rs.getString("org_name"),
                        rs.getObject("event_date", LocalDate.class),
                        EventRole.valueOf(rs.getString("role")),
                        rs.getBoolean("attended")))
                .list();
    }

    @Override
    public List<EventRows.UpcomingEvent> upcomingEventsOfMember(String studentId, LocalDate from, int limit) {
        return jdbc.sql("""
                SELECT e.event_id, e.title, e.org_id, o.name AS org_name, e.event_date
                  FROM events e
                  JOIN organizations o ON o.org_id = e.org_id
                 WHERE e.org_id IN (SELECT org_id FROM org_members WHERE student_id = :student)
                   AND e.event_date >= :from
                 ORDER BY e.event_date, e.title
                 LIMIT :limit
                """)
                .param("student", studentId)
                .param("from", from)
                .param("limit", limit)
                .query((rs, n) -> new EventRows.UpcomingEvent(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("org_id", UUID.class),
                        rs.getString("org_name"),
                        rs.getObject("event_date", LocalDate.class)))
                .list();
    }
}
