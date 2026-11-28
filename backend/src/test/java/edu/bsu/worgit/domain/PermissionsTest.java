package edu.bsu.worgit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.bsu.worgit.exception.ForbiddenException;

class PermissionsTest {

    private static final UUID OWN = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static AuthenticatedUser user(Role role, UUID... orgs) {
        return new AuthenticatedUser("ID", "Name", role, Set.of(orgs));
    }

    @Test
    void studentsHoldNoStaffCapability() {
        for (Capability c : Capability.values()) {
            assertThat(Permissions.has(Role.STUDENT, c)).as(c.name()).isFalse();
        }
    }

    @Test
    void systemAdministratorCanDoEverythingExceptNotifications() {
        for (Capability c : Capability.values()) {
            assertThat(Permissions.has(Role.SYSTEM_ADMINISTRATOR, c)).as(c.name())
                    .isEqualTo(c != Capability.VIEW_NOTIFICATIONS);
        }
    }

    @Test
    void facilitatorManagesPeopleAndEventsButNotTheOrganizationList() {
        Role r = Role.ORGANIZATION_FACILITATOR;
        for (Capability c : new Capability[] { Capability.EDIT_ORGANIZATION_PROFILE, Capability.REVIEW_APPLICATIONS,
                Capability.RECORD_PAYMENTS, Capability.MANAGE_EVENTS, Capability.MODERATE_CHAT,
                Capability.VIEW_NOTIFICATIONS, Capability.VIEW_STUDENT_SUMMARY }) {
            assertThat(Permissions.has(r, c)).as(c.name()).isTrue();
        }
        for (Capability c : new Capability[] { Capability.CREATE_ORGANIZATION, Capability.REMOVE_ORGANIZATION,
                Capability.EDIT_ORGANIZATION_ALL_FIELDS, Capability.OPEN_STUDENT_SUMMARY_PAGE,
                Capability.REMOVE_MEMBER }) {
            assertThat(Permissions.has(r, c)).as(c.name()).isFalse();
        }
    }

    @Test
    void adviserIsReadOnly() {
        Role r = Role.FACULTY_ADVISER;
        for (Capability c : new Capability[] { Capability.VIEW_DASHBOARD, Capability.VIEW_ORGANIZATIONS,
                Capability.VIEW_MEMBERS, Capability.OPEN_STUDENT_SUMMARY_PAGE, Capability.VIEW_APPLICATIONS,
                Capability.VIEW_PAYMENTS, Capability.VIEW_NOTIFICATIONS }) {
            assertThat(Permissions.has(r, c)).as(c.name()).isTrue();
        }
        for (Capability c : new Capability[] { Capability.REVIEW_APPLICATIONS, Capability.RECORD_PAYMENTS,
                Capability.MANAGE_EVENTS, Capability.MODERATE_CHAT, Capability.CREATE_ORGANIZATION,
                Capability.REMOVE_ORGANIZATION, Capability.EDIT_ORGANIZATION_ALL_FIELDS,
                Capability.EDIT_ORGANIZATION_PROFILE, Capability.REMOVE_MEMBER }) {
            assertThat(Permissions.has(r, c)).as(c.name()).isFalse();
        }
    }

    @Test
    void facilitatorIsScopedToManagedOrganizations() {
        AuthenticatedUser fac = user(Role.ORGANIZATION_FACILITATOR, OWN);
        assertThatCode(() -> Permissions.requireForOrg(fac, Capability.REVIEW_APPLICATIONS, OWN))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Permissions.requireForOrg(fac, Capability.REVIEW_APPLICATIONS, OTHER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void systemAdministratorSeesEveryOrganizationWithoutLinks() {
        AuthenticatedUser sys = user(Role.SYSTEM_ADMINISTRATOR);
        assertThatCode(() -> Permissions.requireForOrg(sys, Capability.REVIEW_APPLICATIONS, OTHER))
                .doesNotThrowAnyException();
    }

    @Test
    void adviserCannotActEvenOnManagedOrganizations() {
        AuthenticatedUser adv = user(Role.FACULTY_ADVISER, OWN);
        assertThatThrownBy(() -> Permissions.requireForOrg(adv, Capability.REVIEW_APPLICATIONS, OWN))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void globalActionsNeedTheCapability() {
        assertThatThrownBy(() -> Permissions.requireGlobal(user(Role.ORGANIZATION_FACILITATOR, OWN),
                Capability.CREATE_ORGANIZATION)).isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> Permissions.requireGlobal(user(Role.SYSTEM_ADMINISTRATOR),
                Capability.CREATE_ORGANIZATION)).doesNotThrowAnyException();
    }

    @Test
    void homeScreensAndAdminRoleMapping() {
        assertThat(Role.STUDENT.homeScreen()).isEqualTo("ST-01");
        assertThat(Role.FACULTY_ADVISER.homeScreen()).isEqualTo("AD-01");
        assertThat(Role.fromAdminRole("ORGANIZATION_FACILITATOR")).contains(Role.ORGANIZATION_FACILITATOR);
        assertThat(Role.fromAdminRole("ORGANIZATION_OFFICER")).isEmpty();
        assertThat(Role.fromAdminRole(null)).isEmpty();
    }
}
