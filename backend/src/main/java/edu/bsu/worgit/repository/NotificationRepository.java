package edu.bsu.worgit.repository;

import java.util.List;
import java.util.UUID;

import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.PeopleRows;

public interface NotificationRepository {

    void insertNotification(String recipientId, NotificationType type, String message);

    /** Newest first, at most limit. */
    List<PeopleRows.Notification> listNotifications(String recipientId, int limit);

    int unreadCount(String recipientId);

    /** False when the notification does not exist or belongs to someone else. */
    boolean markRead(UUID notificationId, String recipientId);
}
