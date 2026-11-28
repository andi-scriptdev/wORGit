package edu.bsu.worgit.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.NotificationRepository;

/**
 * The Notifications tab of students, facilitators and advisers (the System Administrator has none).
 * Everyone only ever sees and marks their own notifications. "Mark all as read" is the app calling
 * markRead once per unread item (spec section 6), so there is no separate endpoint for it.
 */
@Service
public class NotificationService {

    static final int LIST_LIMIT = 100;   // ASSUMED: the newest 100

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public PeopleViews.NotificationList list(AuthenticatedUser user) {
        requireTab(user);
        return new PeopleViews.NotificationList(
                notifications.unreadCount(user.userId()),
                notifications.listNotifications(user.userId(), LIST_LIMIT).stream()
                        .map(n -> new PeopleViews.NotificationView(n.notificationId(), n.type(), n.message(),
                                n.createdAt(), n.read()))
                        .toList());
    }

    @Transactional
    public void markRead(AuthenticatedUser user, UUID notificationId) {
        requireTab(user);
        if (!notifications.markRead(notificationId, user.userId())) {
            throw new NotFoundException();
        }
    }

    private static void requireTab(AuthenticatedUser user) {
        if (user.role() != Role.STUDENT) {
            Permissions.requireGlobal(user, Capability.VIEW_NOTIFICATIONS);
        }
    }
}
