package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.support.FakeWorld;

class NotificationServiceTest {

    private FakeWorld w;
    private NotificationService svc;
    private AuthenticatedUser andrea;
    private AuthenticatedUser frenzy;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        andrea = w.addStudent("25-09041", "Andrea Valenzuela");
        w.addStudent("25-00001", "Mika Santos");
        frenzy = w.addStaff("ADM-001", Role.ORGANIZATION_FACILITATOR);
        w.insertNotification("25-09041", NotificationType.APPLICATION, "first");
        w.insertNotification("25-00001", NotificationType.APPLICATION, "not yours");
        w.insertNotification("25-09041", NotificationType.PEER_CONNECTION, "second");
        svc = new NotificationService(w);
    }

    @Test
    void ownNotificationsNewestFirstWithUnreadCount() {
        PeopleViews.NotificationList list = svc.list(andrea);
        assertThat(list.unread()).isEqualTo(2);
        assertThat(list.items()).extracting(PeopleViews.NotificationView::message).containsExactly("second", "first");
    }

    @Test
    void markReadOnlyWorksOnOwnNotifications() {
        UUID first = svc.list(andrea).items().get(1).notificationId();
        svc.markRead(andrea, first);
        PeopleViews.NotificationList list = svc.list(andrea);
        assertThat(list.unread()).isEqualTo(1);
        assertThat(list.items().get(1).read()).isTrue();

        UUID mikas = w.listNotifications("25-00001", 10).get(0).notificationId();
        assertThatThrownBy(() -> svc.markRead(andrea, mikas)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> svc.markRead(andrea, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void facilitatorsAndAdvisersHaveTheTabButNotTheSystemAdministrator() {
        w.insertNotification("ADM-001", NotificationType.APPLICATION, "Andrea applied to Campus Media Network");
        assertThat(svc.list(frenzy).items()).hasSize(1);
        assertThat(svc.list(w.addStaff("ADM-003", Role.FACULTY_ADVISER)).items()).isEmpty();
        AuthenticatedUser charles = w.addStaff("ADM-002", Role.SYSTEM_ADMINISTRATOR);
        assertThatThrownBy(() -> svc.list(charles)).isInstanceOf(ForbiddenException.class);
    }
}
