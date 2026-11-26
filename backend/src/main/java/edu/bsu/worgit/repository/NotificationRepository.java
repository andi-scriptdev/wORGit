package edu.bsu.worgit.repository;

import edu.bsu.worgit.domain.NotificationType;

public interface NotificationRepository {

    void insertNotification(String recipientId, NotificationType type, String message);
}
