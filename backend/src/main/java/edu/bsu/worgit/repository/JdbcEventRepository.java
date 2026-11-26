package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.Rows;

@Repository
public class JdbcEventRepository implements EventRepository {

    private final JdbcClient jdbc;

    public JdbcEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Rows.Event> upcomingEvents(UUID orgId, LocalDate from) {
        return jdbc.sql("""
                SELECT event_id, title, event_date FROM events
                 WHERE org_id = :org AND event_date >= :from
                 ORDER BY event_date, title
                """)
                .param("org", orgId)
                .param("from", from)
                .query((rs, n) -> new Rows.Event(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("event_date", LocalDate.class)))
                .list();
    }
}
