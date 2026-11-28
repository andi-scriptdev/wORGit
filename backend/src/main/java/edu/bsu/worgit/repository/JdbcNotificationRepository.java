package edu.bsu.worgit.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.PeopleRows;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    private final JdbcClient jdbc;

    public JdbcNotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertNotification(String recipientId, NotificationType type, String message) {
        jdbc.sql("INSERT INTO notifications (recipient_id, type, message) VALUES (:recipient, :type, :message)")
                .param("recipient", recipientId)
                .param("type", type.name())
                .param("message", message)
                .update();
    }

    @Override
    public List<PeopleRows.Notification> listNotifications(String recipientId, int limit) {
        return jdbc.sql("""
                SELECT notification_id, type, message, created_at, is_read
                  FROM notifications
                 WHERE recipient_id = :recipient
                 ORDER BY created_at DESC, notification_id
                 LIMIT :limit
                """)
                .param("recipient", recipientId)
                .param("limit", limit)
                .query((rs, n) -> new PeopleRows.Notification(
                        rs.getObject("notification_id", UUID.class),
                        NotificationType.valueOf(rs.getString("type")),
                        rs.getString("message"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getBoolean("is_read")))
                .list();
    }

    @Override
    public int unreadCount(String recipientId) {
        Integer count = jdbc.sql("SELECT count(*) FROM notifications WHERE recipient_id = :recipient AND NOT is_read")
                .param("recipient", recipientId)
                .query(Integer.class).single();
        return count == null ? 0 : count;
    }

    @Override
    public boolean markRead(UUID notificationId, String recipientId) {
        return jdbc.sql("UPDATE notifications SET is_read = true WHERE notification_id = :id AND recipient_id = :recipient")
                .param("id", notificationId)
                .param("recipient", recipientId)
                .update() > 0;
    }
}
