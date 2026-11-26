package edu.bsu.worgit.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.FeeStatus;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.Organization;
import edu.bsu.worgit.domain.PaymentStatus;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.exception.DuplicateMembershipException;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.ApplicationRepository;
import edu.bsu.worgit.repository.MembershipRepository;
import edu.bsu.worgit.repository.NotificationRepository;
import edu.bsu.worgit.repository.OrganizationRepository;
import edu.bsu.worgit.repository.PaymentRepository;
import edu.bsu.worgit.repository.UserRepository;

/** Joining, applying and leaving (students) and member management (staff). */
@Service
public class MembershipService {

    private final OrganizationRepository orgs;
    private final MembershipRepository members;
    private final ApplicationRepository applications;
    private final PaymentRepository payments;
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final Clock clock;

    public MembershipService(OrganizationRepository orgs, MembershipRepository members,
                             ApplicationRepository applications, PaymentRepository payments,
                             NotificationRepository notifications, UserRepository users, Clock clock) {
        this.orgs = orgs;
        this.members = members;
        this.applications = applications;
        this.payments = payments;
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    /** Direct join: only for organizations that do not require approval. */
    @Transactional
    public void join(AuthenticatedUser student, UUID orgId) {
        Permissions.requireStudent(student);
        Organization org = requireOrg(orgId);
        if (members.isMember(student.userId(), orgId)) {
            throw new DuplicateMembershipException(org.name());
        }
        if (org.requiresApproval()) {
            throw new InvalidInputException(Messages.APPROVAL_REQUIRED);
        }
        try {
            addMemberWithFee(org, student.userId());
        } catch (DuplicateKeyException e) {
            throw new DuplicateMembershipException(org.name());
        }
    }

    /** Application for an organization that requires approval; tells the facilitators who manage it. */
    @Transactional
    public void apply(AuthenticatedUser student, UUID orgId) {
        Permissions.requireStudent(student);
        Organization org = requireOrg(orgId);
        if (members.isMember(student.userId(), orgId)) {
            throw new DuplicateMembershipException(org.name());
        }
        if (!org.requiresApproval()) {
            throw new InvalidInputException(Messages.NO_APPROVAL_NEEDED);
        }
        if (applications.hasPendingApplication(student.userId(), orgId)) {
            throw new InvalidInputException(Messages.ALREADY_PENDING);
        }
        try {
            applications.insertApplication(student.userId(), orgId, LocalDate.now(clock));
        } catch (DuplicateKeyException e) {
            throw new InvalidInputException(Messages.ALREADY_PENDING);
        }
        String studentName = users.findById(student.userId()).map(UserAccount::fullName).orElse(student.displayName());
        for (String facilitatorId : users.adminIdsManaging(orgId, Role.ORGANIZATION_FACILITATOR)) {
            notifications.insertNotification(facilitatorId, NotificationType.APPLICATION,
                    Messages.newApplication(studentName, org.name()));
        }
    }

    /** Leaving keeps messages, payments, participation and certificates. */
    @Transactional
    public void leave(AuthenticatedUser student, UUID orgId) {
        Permissions.requireStudent(student);
        Organization org = requireOrg(orgId);
        if (!members.removeMember(orgId, student.userId())) {
            throw new InvalidInputException(Messages.notMember(org.name()));
        }
    }

    /**
     * Adds the member and, for an organization with a fee, an UNPAID payment record for that fee
     * (only if the student has no payment record for the organization yet). Used by join and approve.
     */
    @Transactional
    public void addMemberWithFee(Organization org, String studentId) {
        members.addMember(org.orgId(), studentId, LocalDate.now(clock));
        if (org.membershipFee().signum() > 0 && !payments.hasPaymentRecord(studentId, org.orgId())) {
            payments.insertPayment(studentId, org.orgId(), org.membershipFee(), null, PaymentStatus.UNPAID);
        }
    }

    // ---- staff ----

    public List<Views.MemberView> members(AuthenticatedUser actor, UUID orgId, String search) {
        Permissions.requireForOrg(actor, Capability.VIEW_MEMBERS, orgId);
        requireOrg(orgId);
        Map<String, FeeStatus> fees = payments.feeStatusByStudent(orgId);
        String text = search == null || search.isBlank() ? null : search.strip();
        return members.listMembers(orgId, text).stream()
                .map((Rows.Member m) -> new Views.MemberView(m.studentId(), m.fullName(), m.joinedOn(),
                        fees.getOrDefault(m.studentId(), FeeStatus.NONE)))
                .toList();
    }

    /** System Administrator only (a Facilitator removing members is not specified yet). */
    @Transactional
    public void removeMember(AuthenticatedUser actor, UUID orgId, String studentId) {
        Permissions.requireForOrg(actor, Capability.REMOVE_MEMBER, orgId);
        requireOrg(orgId);
        if (!members.removeMember(orgId, studentId)) {
            throw new NotFoundException();
        }
    }

    private Organization requireOrg(UUID orgId) {
        return orgs.findOrganization(orgId).orElseThrow(NotFoundException::new);
    }
}
