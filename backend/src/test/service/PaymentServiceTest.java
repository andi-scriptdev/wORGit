package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.PaymentStatus;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.support.FakeWorld;
import edu.bsu.worgit.support.TestClock;

class PaymentServiceTest {

    private FakeWorld w;
    private MembershipService memberships;
    private PaymentService svc;
    private UUID robotics;
    private UUID free;
    private UUID other;
    private AuthenticatedUser andrea;
    private AuthenticatedUser frenzy;
    private AuthenticatedUser charles;
    private AuthenticatedUser adviser;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        robotics = w.addOrg("Robotics Club", "Technology", false, "100");
        free = w.addOrg("Web Developers Guild", "Technology", false, "0");
        other = w.addOrg("Green Action", "Advocacy", false, "20");
        andrea = w.addStudent("25-09041", "Andrea Valenzuela");
        w.addStudent("25-09042", "Mika Santos");
        frenzy = w.addStaff("ADM-001", Role.ORGANIZATION_FACILITATOR, robotics, free);
        charles = w.addStaff("ADM-002", Role.SYSTEM_ADMINISTRATOR);
        adviser = w.addStaff("ADM-003", Role.FACULTY_ADVISER, robotics);
        TestClock clock = new TestClock();
        memberships = new MembershipService(w, w, w, w, w, w, clock);
        svc = new PaymentService(w, w, w, w, clock);
    }

    @Test
    void listsAreScopedToTheOrganizationsOfTheStaffMember() {
        memberships.join(andrea, robotics);
        w.addMember(other, "25-09042", LocalDate.of(2026, 9, 1));
        w.insertPayment("25-09042", other, new BigDecimal("20"), null, PaymentStatus.UNPAID);

        assertThat(svc.list(charles, null)).hasSize(2);
        assertThat(svc.list(frenzy, null)).hasSize(1);
        assertThat(svc.list(adviser, null)).hasSize(1);
        assertThat(svc.list(frenzy, robotics)).hasSize(1);
        assertThatThrownBy(() -> svc.list(frenzy, other)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.list(andrea, null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void recordingSettlesTheMembersUnpaidRecord() {
        memberships.join(andrea, robotics);
        svc.record(frenzy, new PaymentService.RecordInput(robotics, "25-09041", new BigDecimal("100"), LocalDate.of(2026, 10, 30)));
        Rows.Payment p = w.allPayments().get(0);
        assertThat(w.allPayments()).hasSize(1);
        assertThat(p.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.datePaid()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void recordingCreatesAPaidRecordWhenTheMemberHasNone() {
        w.addMember(free, "25-09041", LocalDate.of(2026, 9, 1));
        svc.record(frenzy, new PaymentService.RecordInput(free, "25-09041", new BigDecimal("50"), LocalDate.of(2026, 10, 30)));
        assertThat(w.allPayments()).hasSize(1);
        assertThat(w.allPayments().get(0).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(w.allPayments().get(0).amount().compareTo(new BigDecimal("50"))).isZero();
    }

    @Test
    void theDateDefaultsToTodayInManila() {
        w.addMember(free, "25-09041", LocalDate.of(2026, 9, 1));
        svc.record(frenzy, new PaymentService.RecordInput(free, "25-09041", new BigDecimal("50"), null));
        assertThat(w.allPayments().get(0).datePaid()).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void recordingForANonMemberIsRefused() {
        assertThatThrownBy(() -> svc.record(frenzy,
                new PaymentService.RecordInput(robotics, "25-09042", new BigDecimal("100"), null)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Mika Santos is not a member of Robotics Club.");
    }

    @Test
    void theAmountMustBePositive() {
        memberships.join(andrea, robotics);
        assertThatThrownBy(() -> svc.record(frenzy, new PaymentService.RecordInput(robotics, "25-09041", BigDecimal.ZERO, null)))
                .isInstanceOf(InvalidInputException.class).hasMessage(Messages.AMOUNT_POSITIVE);
        assertThatThrownBy(() -> svc.record(frenzy, new PaymentService.RecordInput(robotics, "25-09041", null, null)))
                .isInstanceOf(InvalidInputException.class).hasMessage(Messages.AMOUNT_POSITIVE);
    }

    @Test
    void markPaidSetsTodayAndKeepsTheAmount() {
        memberships.join(andrea, robotics);
        UUID id = w.allPayments().get(0).paymentId();
        svc.markPaid(frenzy, id);
        Rows.Payment p = w.findPayment(id).get();
        assertThat(p.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.datePaid()).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(p.amount().compareTo(new BigDecimal("100"))).isZero();
        assertThatThrownBy(() -> svc.markPaid(frenzy, id))
                .isInstanceOf(InvalidInputException.class).hasMessage(Messages.ALREADY_PAID);
    }

    @Test
    void onlyFacilitatorsOfTheOrganizationAndTheSystemAdministratorRecord() {
        memberships.join(andrea, robotics);
        UUID id = w.allPayments().get(0).paymentId();
        PaymentService.RecordInput in = new PaymentService.RecordInput(robotics, "25-09041", new BigDecimal("100"), null);
        assertThatThrownBy(() -> svc.record(adviser, in)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.record(andrea, in)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.markPaid(adviser, id)).isInstanceOf(ForbiddenException.class);

        w.addMember(other, "25-09042", LocalDate.of(2026, 9, 1));
        w.insertPayment("25-09042", other, new BigDecimal("20"), null, PaymentStatus.UNPAID);
        UUID otherPayment = w.allPayments().get(1).paymentId();
        assertThatThrownBy(() -> svc.markPaid(frenzy, otherPayment)).isInstanceOf(ForbiddenException.class);
        svc.markPaid(charles, otherPayment);
        assertThatThrownBy(() -> svc.markPaid(charles, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
