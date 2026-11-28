package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.InvalidPasswordException;
import edu.bsu.worgit.exception.InvalidProfileException;
import edu.bsu.worgit.support.FakePeople;
import edu.bsu.worgit.support.FakeWorld;

class AccountServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private FakeWorld w;
    private FakePeople p;
    private AccountService svc;
    private AuthenticatedUser andrea;
    private AuthenticatedUser frenzy;
    private AuthenticatedUser charles;
    private UUID media;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        p = new FakePeople(w);
        media = w.addOrg("Campus Media Network", "Communication", true, "0");
        w.addOrg("Robotics Club", "Technology", false, "0");
        andrea = p.student("25-09041", "Andrea", true, "Technology");
        frenzy = w.addStaff("ADM-001", Role.ORGANIZATION_FACILITATOR, media);
        charles = w.addStaff("ADM-002", Role.SYSTEM_ADMINISTRATOR);
        p.setPasswordHash("25-09041", encoder.encode("oldpassword"));
        p.setPasswordHash("ADM-001", encoder.encode("oldpassword"));
        svc = new AccountService(w, p, w, encoder);
    }

    @Test
    void passwordChangesWhenTheCurrentOneMatches() {
        svc.changePassword(andrea, new PeopleViews.PasswordChange("oldpassword", "newpassword"));
        assertThat(encoder.matches("newpassword", p.passwordHashOf("25-09041"))).isTrue();
    }

    @Test
    void wrongCurrentPasswordIsRefusedUnderItsField() {
        assertThatThrownBy(() -> svc.changePassword(andrea, new PeopleViews.PasswordChange("wrong", "newpassword")))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Current password is incorrect.");
        try {
            svc.changePassword(andrea, new PeopleViews.PasswordChange("wrong", "newpassword"));
        } catch (InvalidPasswordException e) {
            assertThat(e.field()).isEqualTo("currentPassword");
        }
        assertThat(encoder.matches("oldpassword", p.passwordHashOf("25-09041"))).isTrue();
    }

    @Test
    void newPasswordNeedsEightCharacters() {
        assertThatThrownBy(() -> svc.changePassword(frenzy, new PeopleViews.PasswordChange("oldpassword", "short")))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Password must be at least 8 characters");
    }

    @Test
    void facilitatorProfileListsManagedOrganizationsReadOnly() {
        PeopleViews.AdminProfile me = svc.adminProfile(frenzy);
        assertThat(me.role()).isEqualTo("ORGANIZATION_FACILITATOR");
        assertThat(me.allOrganizations()).isFalse();
        assertThat(me.managedOrganizations()).extracting(PeopleViews.OrgRef::name).containsExactly("Campus Media Network");
    }

    @Test
    void systemAdministratorManagesAllOrganizations() {
        PeopleViews.AdminProfile me = svc.adminProfile(charles);
        assertThat(me.allOrganizations()).isTrue();
        assertThat(me.managedOrganizations()).isEmpty();
    }

    @Test
    void staffCanChangeOnlyTheDisplayName() {
        PeopleViews.AdminProfile me = svc.updateAdminProfile(frenzy, new PeopleViews.AdminProfileInput(" Frenz "));
        assertThat(me.displayName()).isEqualTo("Frenz");
        assertThat(me.role()).isEqualTo("ORGANIZATION_FACILITATOR");
        assertThatThrownBy(() -> svc.updateAdminProfile(frenzy, new PeopleViews.AdminProfileInput("")))
                .isInstanceOf(InvalidProfileException.class);
    }

    @Test
    void studentsHaveNoAdminProfile() {
        assertThatThrownBy(() -> svc.adminProfile(andrea)).isInstanceOf(ForbiddenException.class);
    }
}
