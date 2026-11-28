package edu.bsu.worgit.repository;

import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.PeopleRows;

@Repository
public class JdbcPeerRepository implements PeerRepository {

    /** The Direct room of a pair, whichever way round the pair was stored. */
    private static final String PAIR_ROOM = """
            SELECT r.room_id FROM chat_rooms r
             WHERE r.room_type = 'PEER'
               AND least(r.student_a_id, r.student_b_id) = least(:a, :b)
               AND greatest(r.student_a_id, r.student_b_id) = greatest(:a, :b)
            """;

    private final JdbcClient jdbc;

    public JdbcPeerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PeopleRows.PeerCandidate> peerCandidates(String studentId) {
        return jdbc.sql("""
                WITH mine AS (SELECT interest FROM student_interests WHERE user_id = :me),
                     conn AS (SELECT CASE WHEN student_a_id = :me THEN student_b_id ELSE student_a_id END AS peer_id
                                FROM peer_connections
                               WHERE :me IN (student_a_id, student_b_id))
                SELECT s.user_id, u.display_name, s.visible_in_matching,
                       coalesce(array_agg(si.interest ORDER BY si.interest)
                                FILTER (WHERE si.interest IN (SELECT interest FROM mine)), '{}') AS shared,
                       (s.user_id IN (SELECT peer_id FROM conn)) AS connected,
                       (SELECT r.room_id FROM chat_rooms r
                         WHERE r.room_type = 'PEER'
                           AND least(r.student_a_id, r.student_b_id) = least(s.user_id, :me)
                           AND greatest(r.student_a_id, r.student_b_id) = greatest(s.user_id, :me)) AS room_id
                  FROM students s
                  JOIN users u ON u.user_id = s.user_id
                  LEFT JOIN student_interests si ON si.user_id = s.user_id
                 WHERE s.user_id <> :me
                 GROUP BY s.user_id, u.display_name, s.visible_in_matching
                HAVING s.user_id IN (SELECT peer_id FROM conn)
                    OR (s.visible_in_matching
                        AND count(si.interest) FILTER (WHERE si.interest IN (SELECT interest FROM mine)) > 0)
                """)
                .param("me", studentId)
                .query((rs, n) -> {
                    boolean connected = rs.getBoolean("connected");
                    return new PeopleRows.PeerCandidate(
                            rs.getString("user_id"),
                            rs.getString("display_name"),
                            toList(rs.getArray("shared")),
                            rs.getBoolean("visible_in_matching"),
                            connected,
                            connected ? rs.getObject("room_id", UUID.class) : null);
                })
                .list();
    }

    private static List<String> toList(Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        try {
            return List.copyOf(Arrays.asList((String[]) array.getArray()));
        } finally {
            array.free();
        }
    }

    @Override
    public boolean isConnected(String studentA, String studentB) {
        Boolean found = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM peer_connections
                                WHERE least(student_a_id, student_b_id) = least(:a, :b)
                                  AND greatest(student_a_id, student_b_id) = greatest(:a, :b))
                """)
                .param("a", studentA)
                .param("b", studentB)
                .query(Boolean.class).single();
        return Boolean.TRUE.equals(found);
    }

    @Override
    public void insertConnection(String fromStudent, String toStudent) {
        jdbc.sql("INSERT INTO peer_connections (student_a_id, student_b_id) VALUES (:a, :b)")
                .param("a", fromStudent)
                .param("b", toStudent)
                .update();
    }

    @Override
    public Optional<UUID> peerRoomId(String studentA, String studentB) {
        return jdbc.sql(PAIR_ROOM)
                .param("a", studentA)
                .param("b", studentB)
                .query((rs, n) -> rs.getObject("room_id", UUID.class))
                .optional();
    }
}
