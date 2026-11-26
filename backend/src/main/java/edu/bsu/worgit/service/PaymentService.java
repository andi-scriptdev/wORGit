package edu.bsu.worgit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.Organization;
import edu.bsu.worgit.domain.PaymentStatus;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.MembershipRepository;
import edu.bsu.worgit.repository.OrganizationRepository;
import edu.bsu.worgit.repository.PaymentRepository;
import edu.bsu.worgit.repository.UserRepository;

/** AD-07: payment records only, there is no online payment. */
@Service
public class PaymentService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    public record RecordInput(UUID orgId, String studentId, BigDecimal amount, LocalDate date) {}

    private final PaymentRepository payments;
    private final OrganizationRepository orgs;
    private final MembershipRepository members;
    private final UserRepository users;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, OrganizationRepository orgs, MembershipRepository members,
                          UserRepository users, Clock clock) {
        this.payments = payments;
        this.orgs = orgs;
        this.members = members;
        this.users = users;
        this.clock = clock;
    }

    public List<Rows.Payment> list(AuthenticatedUser actor, UUID orgId) {
        Permissions.requireGlobal(actor, Capability.VIEW_PAYMENTS);
        if (orgId != null) {
            Permissions.requireForOrg(actor, Capability.VIEW_PAYMENTS, orgId);
        }
        return payments.listPayments(Permissions.visibleOrgIds(actor), orgId);
    }

    /**
     * The Record form: marks the member's unpaid record paid with the given amount and date,
     * or creates a PAID record when the member has none.
     */
    @Transactional
    public void record(AuthenticatedUser actor, RecordInput in) {
        if (in.orgId() == null || in.studentId() == null) {
            throw new InvalidInputException(Messages.INVALID_REQUEST);
        }
        Permissions.requireForOrg(actor, Capability.RECORD_PAYMENTS, in.orgId());
        Organization org = orgs.findOrganization(in.orgId()).orElseThrow(NotFoundException::new);
        if (in.amount() == null || in.amount().signum() <= 0) {
            throw new InvalidInputException(Messages.AMOUNT_POSITIVE, "amount");
        }
        BigDecimal amount = in.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidInputException(Messages.FEE_TOO_LARGE, "amount");
        }
        if (!members.isMember(in.studentId(), in.orgId())) {
            String name = users.findById(in.studentId()).map(UserAccount::fullName).orElse(in.studentId());
            throw new InvalidInputException(Messages.studentNotMember(name, org.name()), "studentId");
        }
        LocalDate date = in.date() == null ? LocalDate.now(clock) : in.date();
        Optional<UUID> unpaid = payments.findUnpaidPaymentId(in.studentId(), in.orgId());
        if (unpaid.isPresent()) {
            payments.settlePayment(unpaid.get(), amount, date);
        } else {
            payments.insertPayment(in.studentId(), in.orgId(), amount, date, PaymentStatus.PAID);
        }
    }

    /** The "Mark paid" button on an unpaid row: paid today (Asia/Manila), same amount. */
    @Transactional
    public void markPaid(AuthenticatedUser actor, UUID paymentId) {
        Rows.Payment payment = payments.findPayment(paymentId).orElseThrow(NotFoundException::new);
        Permissions.requireForOrg(actor, Capability.RECORD_PAYMENTS, payment.orgId());
        if (payment.status() == PaymentStatus.PAID
                || !payments.settlePayment(paymentId, payment.amount(), LocalDate.now(clock))) {
            throw new InvalidInputException(Messages.ALREADY_PAID);
        }
    }
}
