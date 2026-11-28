package edu.bsu.worgit.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.CertificateType;
import edu.bsu.worgit.domain.EventRows;

@Repository
public class JdbcCertificateRepository implements CertificateRepository {

    private static final String SELECT = """
            SELECT c.certificate_id, c.student_id, u.full_name, c.event_id, c.event_title, e.org_id,
                   o.name AS org_name, c.date_issued, c.certificate_type
              FROM certificates c
              JOIN users u ON u.user_id = c.student_id
              JOIN events e ON e.event_id = c.event_id
              JOIN organizations o ON o.org_id = e.org_id
            """;

    private final JdbcClient jdbc;

    public JdbcCertificateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static EventRows.CertificateRow map(ResultSet rs) throws SQLException {
        return new EventRows.CertificateRow(
                rs.getString("certificate_id"),
                rs.getString("student_id"),
                rs.getString("full_name"),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_title"),
                rs.getObject("org_id", UUID.class),
                rs.getString("org_name"),
                rs.getObject("date_issued", LocalDate.class),
                CertificateType.valueOf(rs.getString("certificate_type")));
    }

    @Override
    public Optional<EventRows.CertificateRow> findCertificate(String certificateId) {
        return jdbc.sql(SELECT + " WHERE c.certificate_id = :id")
                .param("id", certificateId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public String insertCertificate(String studentId, UUID eventId, String eventTitle, CertificateType type,
                                    LocalDate issued) {
        return jdbc.sql("""
                INSERT INTO certificates (student_id, event_id, event_title, certificate_type, date_issued)
                VALUES (:student, :event, :title, :type, :issued)
                RETURNING certificate_id
                """)
                .param("student", studentId)
                .param("event", eventId)
                .param("title", eventTitle)
                .param("type", type.name())
                .param("issued", issued)
                .query((rs, n) -> rs.getString("certificate_id"))
                .single();
    }

    @Override
    public List<EventRows.CertificateRow> certificatesOf(String studentId) {
        return jdbc.sql(SELECT + " WHERE c.student_id = :student ORDER BY c.date_issued DESC, c.certificate_id DESC")
                .param("student", studentId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public int certificateCount(UUID eventId) {
        Integer n = jdbc.sql("SELECT count(*) FROM certificates WHERE event_id = :id")
                .param("id", eventId)
                .query(Integer.class).single();
        return n == null ? 0 : n;
    }
}
