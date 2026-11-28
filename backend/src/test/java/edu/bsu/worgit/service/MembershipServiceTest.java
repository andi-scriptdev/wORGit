package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.ApplicationStatus;
import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.FeeStatus;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.PaymentStatus;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.exception.DuplicateMembershipException;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.support.FakeWorld;
import edu.bsu.worgit.support.TestClock;

class MembershipServiceTest {

    private FakeWorld w;
    private MembershipService svc;
    private UUID free;
    private UUID approval;
    private UUID paid;
    private AuthenticatedUser andrea;
    private AuthenticatedUser frenzy;
    private AuthenticatedUser charles;
    private AuthenticatedUser adviser;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        free = w.addOrg("Web Developers Guild", "Technology", false, "0");
        approval = w.addOrg("Campus Media Network", "Communication", true, "0");
        paid = w.addOrg("Robotics Club", "Technology", false, "100");
        andrea = w.addStudent("25-09041", "Andrea Valenzuela");
        frenzy = w.addStaff("ADM-001", Role.ORGANIZATION_FACILITATOR, approval);
        charles = w.addStaff("ADM-002", Role.SYSTEM_ADMINISTRATOR);
        adviser = w.addStaff("ADM-003", Role.FACULTY_ADVISER, approval);
        svc = new MembershipService(w, w, w, w, w, w, new TestClock());
    }

    @Test
    void joinFreeOrganizationAddsAMemberWithoutAFeeRecord() {
        svc.join(andrea, free);
        assertThat(w.isMember("25-09041", free)).isTrue();
        assertThat(w.allPayments()).isEmpty();
    }

    @Test
    void organizationsWithApprovalCannotBeJoinedDirectly() {
        assertThatThrownBy(() -> svc.join(andrea, approval))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage(Messages.APPROVAL_REQUIRED);
        assertThat(w.isMember("25-09041", approval)).isFalse();
    }

    @Test
    void joiningTwiceIsRefusedWithTheSpecMessage() {
        svc.join(andrea, free);
        assertThatThrownBy(() -> svc.join(andrea, free))
                .isInstanceOf(DuplicateMembershipException.class)
                .hasMessage("You are already a member of Web Developers Guild.");
    }

    @Test
    void joiningAFeeOrganizationCreatesAnUnpaidRecordForTheFee() {
        svc.join(andrea, paid);
        List<Rows.Payment> payments = w.allPayments();
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).status()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payments.get(0).amount().compareTo(new BigDecimal("100"))).isZero();
        assertThat(payments.get(0).datePaid()).isNull();
        assertThat(w.feeStatusOf("25-09041", paid)).isEqualTo(FeeStatus.UNPAID);
    }

    @Test
    void leavingAndRejoiningDoesNotCreateASecondFeeRecord() {
        svc.join(andrea, paid);
        svc.leave(andrea, paid);
        svc.join(andrea, paid);
        assertThat(w.allPayments()).hasSize(1);
    }

    @Test
    void leavingKeepsPaymentRecords() {
        svc.join(andrea, paid);
        svc.leave(andrea, paid);
        assertThat(w.isMember("25-09041", paid)).isFalse();
        assertThat(w.allPayments()).hasSize(1);
    }

    @Test
    void leavingWithoutBeingAMemberIsRefused() {
        assertThatThrownBy(() -> svc.leave(andrea, free))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("You are not a member of Web Developers Guild.");
    }

    @Test
    void unknownOrganizationIsNotFound() {
        assertThatThrownBy(() -> svc.join(andrea, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void applyingCreatesAPendingApplicationAndNotifiesOnlyTheFacilitators() {
        svc.apply(andrea, approval);
        assertThat(w.hasPendingApplication("25-09041", approval)).isTrue();
        assertThat(w.notifications()).hasSize(1);
        FakeWorld.Note note = w.notifications().get(0);
        assertThat(note.recipientId()).isEqualTo("ADM-001");
        assertThat(note.type()).isEqualTo(NotificationType.APPLICATION);
        assertThat(note.message()).isEqualTo("Andrea Valenzuela applied to Campus Media Network");
    }

    @Test
    void applyingTwiceIsRefused() {
        svc.apply(andrea, approval);
        assertThatThrownBy(() -> svc.apply(andrea, approval))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage(Messages.ALREADY_PENDING);
    }

    @Test
    void organizationsWithoutApprovalCannotBeAppliedTo() {
        assertThatThrownBy(() -> svc.apply(andrea, free))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage(Messages.NO_APPROVAL_NEEDED);
    }

    @Test
    void membersCannotApply() {
        w.addMember(approval, "25-09041", java.time.LocalDate.of(2026, 9, 1));
        assertThatThrownBy(() -> svc.apply(andrea, approval)).isInstanceOf(DuplicateMembershipException.class);
    }

    @Test
    void applyingAgainAfterARejectionIsAllowed() {
        svc.apply(andrea, approval);
        UUID id = w.listApplicationsOf("25-09041").get(0).applicationId();
        w.decideApplication(id, ApplicationStatus.REJECTED);
        svc.apply(andrea, approval);
        assertThat(w.listApplicationsOf("25-09041")).hasSize(2);
        assertThat(w.hasPendingApplication("25-09041", approval)).isTrue();
    }

    @Test
    void staffCannotUseTheStudentActions() {
        assertThatThrownBy(() -> svc.join(charles, free)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.apply(frenzy, approval)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.leave(adviser, approval)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void onlyTheSystemAdministratorRemovesMembers() {
        svc.join(andrea, free);
        assertThatThrownBy(() -> svc.removeMember(frenzy, free, "25-09041")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.removeMember(adviser, free, "25-09041")).isInstanceOf(ForbiddenException.class);
        svc.removeMember(charles, free, "25-09041");
        assertThat(w.isMember("25-09041", free)).isFalse();
        assertThatThrownBy(() -> svc.removeMember(charles, free, "25-09041")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void memberListShowsFeeStatusAndIsScopedToManagedOrganizations() {
        w.addStudent("25-09042", "Mika Santos");
        svc.join(andrea, paid);
        w.addMember(paid, "25-09042", java.time.LocalDate.of(2026, 9, 1));   // no payment record
        List<Views.MemberView> members = svc.members(charles, paid, null);
        assertThat(members).hasSize(2);
        assertThat(members.get(0).fullName()).isEqualTo("Andrea Valenzuela");
        assertThat(members.get(0).feeStatus()).isEqualTo(FeeStatus.UNPAID);
        assertThat(members.get(1).feeStatus()).isEqualTo(FeeStatus.NONE);
        assertThat(svc.members(charles, paid, "mika")).hasSize(1);

        assertThatThrownBy(() -> svc.members(frenzy, paid, null)).isInstanceOf(ForbiddenException.class);
        assertThat(svc.members(adviser, approval, null)).isEmpty();
        assertThatThrownBy(() -> svc.members(andrea, paid, null)).isInstanceOf(ForbiddenException.class);
    }
}
