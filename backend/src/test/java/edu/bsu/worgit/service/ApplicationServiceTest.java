package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.ApplicationStatus;
import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.PaymentStatus;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.support.FakeWorld;
import edu.bsu.worgit.support.TestClock;

class ApplicationServiceTest {

    private FakeWorld w;
    private MembershipService memberships;
    private ApplicationService svc;
    private UUID media;
    private UUID toastmasters;
    private UUID other;
    private AuthenticatedUser andrea;
    private AuthenticatedUser frenzy;
    private AuthenticatedUser charles;
    private AuthenticatedUser adviser;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        media = w.addOrg("Campus Media Network", "Communication", true, "0");
        toastmasters = w.addOrg("Toastmasters", "Communication", true, "50");
        other = w.addOrg("Green Action", "Advocacy", true, "0");
        andrea = w.addStudent("25-09041", "Andrea Valenzuela");
        frenzy = w.addStaff("ADM-001", Role.ORGANIZATION_FACILITATOR, media, toastmasters);
        charles = w.addStaff("ADM-002", Role.SYSTEM_ADMINISTRATOR);
        adviser = w.addStaff("ADM-003", Role.FACULTY_ADVISER, media);
        memberships = new MembershipService(w, w, w, w, w, w, new TestClock());
        svc = new ApplicationService(w, w, w, memberships, w, w);
    }

    private UUID apply(UUID org) {
        memberships.apply(andrea, org);
        return w.listApplications(ApplicationStatus.PENDING, null, org).get(0).applicationId();
    }

    @Test
    void approvingAddsTheMemberAndNotifiesTheStudentAndTheAdviser() {
        UUID id = apply(media);
        svc.approve(frenzy, id);
        assertThat(w.isMember("25-09041", media)).isTrue();
        assertThat(w.findApplication(id).get().status()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(w.messagesFor("25-09041")).contains("Application to Campus Media Network was approved");
        assertThat(w.messagesFor("ADM-003"))
                .contains("Application of Andrea Valenzuela to Campus Media Network was approved");
    }

    @Test
    void approvingAFeeOrganizationCreatesAnUnpaidRecord() {
        UUID id = apply(toastmasters);
        svc.approve(charles, id);
        assertThat(w.allPayments()).hasSize(1);
        assertThat(w.allPayments().get(0).status()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void rejectingDoesNotAddAMemberAndNotifiesTheStudent() {
        UUID id = apply(media);
        svc.reject(frenzy, id);
        assertThat(w.isMember("25-09041", media)).isFalse();
        assertThat(w.findApplication(id).get().status()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(w.messagesFor("25-09041")).contains("Application to Campus Media Network was rejected");
    }

    @Test
    void anApplicationCanOnlyBeDecidedOnce() {
        UUID id = apply(media);
        svc.approve(frenzy, id);
        assertThatThrownBy(() -> svc.reject(frenzy, id))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage(Messages.ALREADY_DECIDED);
    }

    @Test
    void facilitatorsOnlyDecideApplicationsOfTheirOwnOrganizations() {
        UUID id = apply(other);
        assertThatThrownBy(() -> svc.approve(frenzy, id)).isInstanceOf(ForbiddenException.class);
        svc.approve(charles, id);
        assertThat(w.isMember("25-09041", other)).isTrue();
    }

    @Test
    void advisersAndStudentsCannotDecide() {
        UUID id = apply(media);
        assertThatThrownBy(() -> svc.approve(adviser, id)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.reject(andrea, id)).isInstanceOf(ForbiddenException.class);
        assertThat(w.findApplication(id).get().status()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    void unknownApplicationIsNotFound() {
        assertThatThrownBy(() -> svc.approve(charles, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsAreScopedAndCarryTheTabCounts() {
        UUID first = apply(media);
        apply(other);
        svc.reject(frenzy, first);
        apply(media);

        Views.ApplicationList mine = svc.list(frenzy, null, null);
        assertThat(mine.items()).hasSize(2);
        assertThat(mine.pending()).isEqualTo(1);
        assertThat(mine.rejected()).isEqualTo(1);
        assertThat(mine.approved()).isEqualTo(0);

        Views.ApplicationList all = svc.list(charles, null, null);
        assertThat(all.items()).hasSize(3);
        assertThat(all.pending()).isEqualTo(2);

        assertThat(svc.list(charles, ApplicationStatus.PENDING, null).items()).hasSize(2);
        assertThat(svc.list(adviser, null, null).items()).hasSize(2);
        assertThat(svc.list(frenzy, null, media).items()).hasSize(2);
    }

    @Test
    void listingAnOrganizationOutsideTheScopeIsRefused() {
        assertThatThrownBy(() -> svc.list(frenzy, null, other)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.list(andrea, null, null)).isInstanceOf(ForbiddenException.class);
    }
}
