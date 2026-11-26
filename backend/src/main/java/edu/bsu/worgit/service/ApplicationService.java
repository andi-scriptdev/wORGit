package edu.bsu.worgit.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.ApplicationStatus;
import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.Organization;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.ApplicationRepository;
import edu.bsu.worgit.repository.MembershipRepository;
import edu.bsu.worgit.repository.NotificationRepository;
import edu.bsu.worgit.repository.OrganizationRepository;
import edu.bsu.worgit.repository.UserRepository;

/** AD-06: reviewing membership applications. */
@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final OrganizationRepository orgs;
    private final MembershipRepository members;
    private final MembershipService membershipService;
    private final NotificationRepository notifications;
    private final UserRepository users;

    public ApplicationService(ApplicationRepository applications, OrganizationRepository orgs,
                              MembershipRepository members, MembershipService membershipService,
                              NotificationRepository notifications, UserRepository users) {
        this.applications = applications;
        this.orgs = orgs;
        this.members = members;
        this.membershipService = membershipService;
        this.notifications = notifications;
        this.users = users;
    }

    /** status null = all statuses; orgId null = every organization the staff member may see. */
    public Views.ApplicationList list(AuthenticatedUser actor, ApplicationStatus status, UUID orgId) {
        Permissions.requireGlobal(actor, Capability.VIEW_APPLICATIONS);
        if (orgId != null) {
            Permissions.requireForOrg(actor, Capability.VIEW_APPLICATIONS, orgId);
        }
        Set<UUID> scope = Permissions.visibleOrgIds(actor);
        Map<ApplicationStatus, Integer> counts = applications.countApplications(scope, orgId);
        return new Views.ApplicationList(
                counts.getOrDefault(ApplicationStatus.PENDING, 0),
                counts.getOrDefault(ApplicationStatus.APPROVED, 0),
                counts.getOrDefault(ApplicationStatus.REJECTED, 0),
                applications.listApplications(status, scope, orgId));
    }

    @Transactional
    public void approve(AuthenticatedUser actor, UUID applicationId) {
        decide(actor, applicationId, true);
    }

    @Transactional
    public void reject(AuthenticatedUser actor, UUID applicationId) {
        decide(actor, applicationId, false);
    }

    private void decide(AuthenticatedUser actor, UUID applicationId, boolean approve) {
        Rows.Application app = applications.findApplication(applicationId).orElseThrow(NotFoundException::new);
        Permissions.requireForOrg(actor, Capability.REVIEW_APPLICATIONS, app.orgId());
        if (app.status() != ApplicationStatus.PENDING) {
            throw new InvalidInputException(Messages.ALREADY_DECIDED);
        }
        Organization org = orgs.findOrganization(app.orgId()).orElseThrow(NotFoundException::new);

        if (!applications.decideApplication(applicationId, approve ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED)) {
            throw new InvalidInputException(Messages.ALREADY_DECIDED);
        }
        if (approve && !members.isMember(app.studentId(), app.orgId())) {
            try {
                membershipService.addMemberWithFee(org, app.studentId());
            } catch (DuplicateKeyException e) {
                // became a member in the meantime: nothing more to do
            }
        }

        notifications.insertNotification(app.studentId(), NotificationType.APPLICATION,
                Messages.applicationDecided(org.name(), approve));
        for (String adviserId : users.adminIdsManaging(app.orgId(), Role.FACULTY_ADVISER)) {
            notifications.insertNotification(adviserId, NotificationType.APPLICATION,
                    Messages.applicationDecidedForAdviser(app.studentName(), org.name(), approve));
        }
    }
}
