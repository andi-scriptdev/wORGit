package edu.bsu.worgit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.ApplicationStatus;
import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.OrgInput;
import edu.bsu.worgit.domain.OrgSummary;
import edu.bsu.worgit.domain.Organization;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.domain.Rules;
import edu.bsu.worgit.domain.SocialLink;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.ApplicationRepository;
import edu.bsu.worgit.repository.EventRepository;
import edu.bsu.worgit.repository.MembershipRepository;
import edu.bsu.worgit.repository.OrganizationRepository;
import edu.bsu.worgit.repository.PaymentRepository;

/** Organizations for students (ST-02, ST-03) and for staff (AD-02, AD-03). */
@Service
public class OrganizationService {

    private static final BigDecimal MAX_FEE = new BigDecimal("99999999.99");   // numeric(10,2)

    private final OrganizationRepository orgs;
    private final MembershipRepository members;
    private final ApplicationRepository applications;
    private final PaymentRepository payments;
    private final EventRepository events;
    private final Clock clock;

    public OrganizationService(OrganizationRepository orgs, MembershipRepository members,
                               ApplicationRepository applications, PaymentRepository payments,
                               EventRepository events, Clock clock) {
        this.orgs = orgs;
        this.members = members;
        this.applications = applications;
        this.payments = payments;
        this.events = events;
        this.clock = clock;
    }

    // ================= students =================

    /** ST-02 "Browse": search text and category chip are optional. */
    public List<Views.OrgCard> browse(AuthenticatedUser student, String search, String category) {
        Permissions.requireStudent(student);
        return toCards(student.userId(), orgs.searchOrganizations(blankToNull(search), blankToNull(category), null));
    }

    /** ST-02 "My organizations". */
    public List<Views.OrgCard> mine(AuthenticatedUser student) {
        Permissions.requireStudent(student);
        Set<UUID> ids = members.memberOrgIds(student.userId());
        return toCards(student.userId(), orgs.searchOrganizations(null, null, ids));
    }

    /** ST-02 "My applications". */
    public List<Views.MyApplication> myApplications(AuthenticatedUser student) {
        Permissions.requireStudent(student);
        return applications.listApplicationsOf(student.userId()).stream()
                .map(a -> new Views.MyApplication(a.applicationId(), a.orgId(), a.organizationName(),
                        a.dateApplied(), a.status()))
                .toList();
    }

    /** ST-03. */
    public Views.OrgDetail detail(AuthenticatedUser student, UUID orgId) {
        Permissions.requireStudent(student);
        Organization org = requireOrg(orgId);
        boolean member = members.isMember(student.userId(), orgId);
        boolean pending = !member && applications.hasPendingApplication(student.userId(), orgId);
        Views.Membership membership = new Views.Membership(
                stateOf(org.requiresApproval(), member, pending),
                member ? payments.feeStatusOf(student.userId(), orgId) : null,
                member ? orgs.organizationRoomId(orgId).orElse(null) : null);
        return new Views.OrgDetail(org.orgId(), org.name(), org.category(), org.logoUrl(), org.coverPhotoUrl(),
                org.memberCount(), org.interests(), org.description(), org.contactEmail(), org.contactPhone(),
                org.socialLinks(), org.requiresApproval(), events.upcomingEvents(orgId, LocalDate.now(clock)),
                membership);
    }

    private List<Views.OrgCard> toCards(String studentId, List<OrgSummary> found) {
        Set<UUID> joined = members.memberOrgIds(studentId);
        Set<UUID> pending = applications.listApplicationsOf(studentId).stream()
                .filter(a -> a.status() == ApplicationStatus.PENDING)
                .map(Rows.Application::orgId)
                .collect(Collectors.toSet());
        return found.stream()
                .map(o -> new Views.OrgCard(o.orgId(), o.name(), o.category(), o.logoUrl(), o.coverPhotoUrl(),
                        o.memberCount(), o.requiresApproval(),
                        stateOf(o.requiresApproval(), joined.contains(o.orgId()), pending.contains(o.orgId()))))
                .toList();
    }

    static Views.CardState stateOf(boolean requiresApproval, boolean member, boolean pending) {
        if (member) {
            return Views.CardState.JOINED;
        }
        if (pending) {
            return Views.CardState.PENDING;
        }
        return requiresApproval ? Views.CardState.APPLY : Views.CardState.JOIN;
    }

    // ================= staff =================

    /** AD-02: System Administrator sees all organizations, others only the ones they manage. */
    public List<Views.StaffOrgRow> listForStaff(AuthenticatedUser actor, String search) {
        Permissions.requireGlobal(actor, Capability.VIEW_ORGANIZATIONS);
        boolean allFields = Permissions.has(actor.role(), Capability.EDIT_ORGANIZATION_ALL_FIELDS);
        boolean profile = Permissions.has(actor.role(), Capability.EDIT_ORGANIZATION_PROFILE);
        boolean remove = Permissions.has(actor.role(), Capability.REMOVE_ORGANIZATION);
        return orgs.searchOrganizations(blankToNull(search), null, Permissions.visibleOrgIds(actor)).stream()
                .map(o -> new Views.StaffOrgRow(o.orgId(), o.name(), o.category(), o.memberCount(),
                        o.requiresApproval(), o.membershipFee(), profile && actor.canManage(o.orgId()),
                        allFields, remove))
                .toList();
    }

    /** AD-03 form data. */
    public Organization getForStaff(AuthenticatedUser actor, UUID orgId) {
        Permissions.requireForOrg(actor, Capability.VIEW_ORGANIZATIONS, orgId);
        return requireOrg(orgId);
    }

    @Transactional
    public UUID create(AuthenticatedUser actor, OrgInput input) {
        Permissions.requireGlobal(actor, Capability.CREATE_ORGANIZATION);
        OrgInput clean = validate(input, null);
        try {
            return orgs.insertOrganization(clean);
        } catch (DuplicateKeyException e) {
            throw new InvalidInputException(Messages.nameTaken(clean.name()), "name");
        }
    }

    /**
     * System Administrator: every field. Facilitator (for their own organizations): profile content only;
     * a request that changes name, category, approval flag or fee is refused (ASSUMED).
     */
    @Transactional
    public void update(AuthenticatedUser actor, UUID orgId, OrgInput input) {
        Permissions.requireForOrg(actor, Capability.EDIT_ORGANIZATION_PROFILE, orgId);
        Organization current = requireOrg(orgId);
        if (!Permissions.has(actor.role(), Capability.EDIT_ORGANIZATION_ALL_FIELDS)) {
            boolean feeChanged = input.membershipFee() != null
                    && input.membershipFee().compareTo(current.membershipFee()) != 0;
            boolean restrictedChanged = !Objects.equals(input.name() == null ? null : input.name().strip(), current.name())
                    || !Objects.equals(input.category(), current.category())
                    || input.requiresApproval() != current.requiresApproval()
                    || feeChanged;
            if (restrictedChanged) {
                throw new ForbiddenException();
            }
        }
        OrgInput clean = validate(input.membershipFee() == null
                ? withFee(input, current.membershipFee()) : input, orgId);
        try {
            orgs.updateOrganization(orgId, clean);
        } catch (DuplicateKeyException e) {
            throw new InvalidInputException(Messages.nameTaken(clean.name()), "name");
        }
    }

    /** Numbers shown in the confirmation before removing an organization. */
    public Rows.RemovalImpact removalImpact(AuthenticatedUser actor, UUID orgId) {
        Permissions.requireForOrg(actor, Capability.REMOVE_ORGANIZATION, orgId);
        requireOrg(orgId);
        return orgs.removalImpact(orgId);
    }

    /** Blocked while the organization has issued certificates (they would be deleted with its events). */
    @Transactional
    public void remove(AuthenticatedUser actor, UUID orgId) {
        Permissions.requireForOrg(actor, Capability.REMOVE_ORGANIZATION, orgId);
        Organization org = requireOrg(orgId);
        Rows.RemovalImpact impact = orgs.removalImpact(orgId);
        if (impact.certificates() > 0) {
            throw new InvalidInputException(Messages.cannotRemoveOrg(org.name(), impact.certificates()));
        }
        orgs.deleteOrganization(orgId);
    }

    // ================= validation =================

    private OrgInput validate(OrgInput in, UUID exceptOrgId) {
        String name = in.name() == null ? "" : in.name().strip();
        if (name.isEmpty()) {
            throw new InvalidInputException(Messages.NAME_REQUIRED, "name");
        }
        Rules.requireOrgText("Name", "name", name);

        Set<String> allowed = orgs.allowedInterests();
        if (in.category() == null || !allowed.contains(in.category())) {
            throw new InvalidInputException(Messages.BAD_CATEGORY, "category");
        }

        String description = Rules.requireOrgText("Description", "description",
                in.description() == null ? "" : in.description());
        String email = optionalText("Contact email", "contactEmail", in.contactEmail());
        String phone = optionalText("Contact phone", "contactPhone", in.contactPhone());

        BigDecimal fee = in.membershipFee() == null ? BigDecimal.ZERO : in.membershipFee();
        if (fee.signum() < 0) {
            throw new InvalidInputException(Messages.FEE_NEGATIVE, "membershipFee");
        }
        fee = fee.setScale(2, RoundingMode.HALF_UP);
        if (fee.compareTo(MAX_FEE) > 0) {
            throw new InvalidInputException(Messages.FEE_TOO_LARGE, "membershipFee");
        }

        Set<String> interests = new LinkedHashSet<>();
        if (in.interests() != null) {
            for (String interest : in.interests()) {
                if (interest == null || !allowed.contains(interest)) {
                    throw new InvalidInputException(Messages.UNKNOWN_INTEREST, "interests");
                }
                interests.add(interest);
            }
        }

        List<SocialLink> links = new ArrayList<>();
        if (in.socialLinks() != null) {
            for (SocialLink link : in.socialLinks()) {
                String platform = link == null || link.platform() == null ? "" : link.platform().strip();
                String url = link == null || link.url() == null ? "" : link.url().strip();
                String lower = url.toLowerCase();
                if (platform.isEmpty() || !(lower.startsWith("http://") || lower.startsWith("https://"))) {
                    throw new InvalidInputException(Messages.BAD_SOCIAL_LINK, "socialLinks");
                }
                Rules.requireOrgText("Social link platform", "socialLinks", platform);
                Rules.requireOrgText("Social link URL", "socialLinks", url);
                links.add(new SocialLink(platform, url));
            }
        }

        if (orgs.organizationNameTaken(name, exceptOrgId)) {
            throw new InvalidInputException(Messages.nameTaken(name), "name");
        }
        return new OrgInput(name, in.category(), description, in.requiresApproval(), fee, email, phone,
                List.copyOf(interests), List.copyOf(links));
    }

    private static String optionalText(String label, String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Rules.requireOrgText(label, field, value.strip());
    }

    private static OrgInput withFee(OrgInput in, BigDecimal fee) {
        return new OrgInput(in.name(), in.category(), in.description(), in.requiresApproval(), fee,
                in.contactEmail(), in.contactPhone(), in.interests(), in.socialLinks());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private Organization requireOrg(UUID orgId) {
        return orgs.findOrganization(orgId).orElseThrow(NotFoundException::new);
    }
}
