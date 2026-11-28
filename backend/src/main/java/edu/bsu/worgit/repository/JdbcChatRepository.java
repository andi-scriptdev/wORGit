package edu.bsu.worgit.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.PeopleRows;
import edu.bsu.worgit.domain.RoomType;

@Repository
public class JdbcChatRepository implements ChatRepository {

    private static final String ROOM_COLUMNS =
            "r.room_id, r.room_type, r.name, r.interest, r.org_id, r.student_a_id, r.student_b_id";

    /**
     * Rooms with a title and the latest message. :me is the viewer, used for the Direct room title
     * (the other student's display name); organization rooms use the organization's current name.
     */
    private static final String LISTING = """
            SELECT %s,
                   CASE r.room_type WHEN 'ORGANIZATION' THEN o.name
                                    WHEN 'PEER' THEN pu.display_name
                                    ELSE r.name END AS title,
                   lm.sender_name AS last_sender, lm.message AS last_message, lm.sent_at AS last_sent_at
              FROM chat_rooms r
              LEFT JOIN organizations o ON o.org_id = r.org_id
              LEFT JOIN users pu ON pu.user_id = CASE WHEN r.student_a_id = :me THEN r.student_b_id
                                                      ELSE r.student_a_id END
              LEFT JOIN LATERAL (SELECT m.sender_name, m.message, m.sent_at
                                   FROM chat_messages m
                                  WHERE m.room_id = r.room_id
                                  ORDER BY m.id DESC
                                  LIMIT 1) lm ON true
            """.formatted(ROOM_COLUMNS);

    private static final String MESSAGE_COLUMNS = "m.id, m.room_id, m.sender_id, m.sender_name, m.message, m.sent_at";

    private final JdbcClient jdbc;

    public JdbcChatRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static PeopleRows.Room mapRoom(ResultSet rs) throws SQLException {
        return new PeopleRows.Room(
                rs.getObject("room_id", UUID.class),
                RoomType.valueOf(rs.getString("room_type")),
                rs.getString("name"),
                rs.getString("interest"),
                rs.getObject("org_id", UUID.class),
                rs.getString("student_a_id"),
                rs.getString("student_b_id"));
    }

    private static PeopleRows.RoomListing mapListing(ResultSet rs) throws SQLException {
        return new PeopleRows.RoomListing(mapRoom(rs), rs.getString("title"), rs.getString("last_sender"),
                rs.getString("last_message"), instant(rs, "last_sent_at"));
    }

    private static PeopleRows.ChatMessage mapMessage(ResultSet rs) throws SQLException {
        return new PeopleRows.ChatMessage(
                rs.getLong("id"),
                rs.getObject("room_id", UUID.class),
                rs.getString("sender_id"),
                rs.getString("sender_name"),
                rs.getString("message"),
                instant(rs, "sent_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    @Override
    public Optional<PeopleRows.Room> findRoom(UUID roomId) {
        return jdbc.sql("SELECT " + ROOM_COLUMNS + " FROM chat_rooms r WHERE r.room_id = :id")
                .param("id", roomId)
                .query((rs, n) -> mapRoom(rs))
                .optional();
    }

    @Override
    public List<PeopleRows.RoomListing> studentRooms(String studentId) {
        return jdbc.sql(LISTING + """
                 WHERE r.room_type IN ('LOBBY', 'INTEREST')
                    OR (r.room_type = 'ORGANIZATION'
                        AND r.org_id IN (SELECT org_id FROM org_members WHERE student_id = :me))
                    OR (r.room_type = 'PEER' AND :me IN (r.student_a_id, r.student_b_id))
                """)
                .param("me", studentId)
                .query((rs, n) -> mapListing(rs))
                .list();
    }

    @Override
    public List<PeopleRows.RoomListing> moderationRooms(boolean includeCampusRooms, Set<UUID> orgScope) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("me", "");   // no viewer: Direct rooms are never listed here
        if (includeCampusRooms) {
            conditions.add("r.room_type IN ('LOBBY', 'INTEREST')");
        }
        if (orgScope == null) {
            conditions.add("r.room_type = 'ORGANIZATION'");
        } else if (!orgScope.isEmpty()) {
            conditions.add("(r.room_type = 'ORGANIZATION' AND r.org_id IN (:scope))");
            params.put("scope", orgScope);
        }
        if (conditions.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(LISTING + " WHERE " + String.join(" OR ", conditions))
                .params(params)
                .query((rs, n) -> mapListing(rs))
                .list();
    }

    @Override
    public List<PeopleRows.ChatMessage> messages(UUID roomId, Long beforeId, Long afterId, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("room", roomId);
        params.put("limit", limit);
        if (afterId != null) {
            params.put("after", afterId);
            return jdbc.sql("SELECT " + MESSAGE_COLUMNS + """
                     FROM chat_messages m
                    WHERE m.room_id = :room AND m.id > :after
                    ORDER BY m.id
                    LIMIT :limit
                    """)
                    .params(params)
                    .query((rs, n) -> mapMessage(rs))
                    .list();
        }
        String older = "";
        if (beforeId != null) {
            older = " AND m.id < :before";
            params.put("before", beforeId);
        }
        List<PeopleRows.ChatMessage> newestFirst = new ArrayList<>(jdbc.sql("SELECT " + MESSAGE_COLUMNS
                        + " FROM chat_messages m WHERE m.room_id = :room" + older + " ORDER BY m.id DESC LIMIT :limit")
                .params(params)
                .query((rs, n) -> mapMessage(rs))
                .list());
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    @Override
    public PeopleRows.ChatMessage insertMessage(UUID roomId, String senderId, String senderName, String message) {
        return jdbc.sql("""
                INSERT INTO chat_messages (room_id, sender_id, sender_name, message)
                VALUES (:room, :sender, :name, :message)
                RETURNING id, room_id, sender_id, sender_name, message, sent_at
                """)
                .param("room", roomId)
                .param("sender", senderId)
                .param("name", senderName)
                .param("message", message)
                .query((rs, n) -> mapMessage(rs))
                .single();
    }

    @Override
    public Optional<PeopleRows.ChatMessage> findMessage(long messageId) {
        return jdbc.sql("SELECT " + MESSAGE_COLUMNS + " FROM chat_messages m WHERE m.id = :id")
                .param("id", messageId)
                .query((rs, n) -> mapMessage(rs))
                .optional();
    }

    @Override
    public boolean deleteMessage(long messageId) {
        return jdbc.sql("DELETE FROM chat_messages WHERE id = :id")
                .param("id", messageId)
                .update() > 0;
    }
}
