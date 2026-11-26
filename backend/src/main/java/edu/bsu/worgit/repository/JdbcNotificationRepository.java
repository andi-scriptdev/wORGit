package edu.bsu.worgit.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.NotificationType;

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
}
