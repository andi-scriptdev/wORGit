package edu.bsu.worgit.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import edu.bsu.worgit.domain.ApplicationStatus;
import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.FeeStatus;
import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.OrgInput;
import edu.bsu.worgit.domain.OrgSummary;
import edu.bsu.worgit.domain.Organization;
import edu.bsu.worgit.domain.PaymentStatus;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.domain.SocialLink;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.repository.ApplicationRepository;
import edu.bsu.worgit.repository.EventRepository;
import edu.bsu.worgit.repository.MembershipRepository;
import edu.bsu.worgit.repository.NotificationRepository;
import edu.bsu.worgit.repository.OrganizationRepository;
import edu.bsu.worgit.repository.PaymentRepository;
import edu.bsu.worgit.repository.UserRepository;

/** An in-memory database behind every repository interface of slice 1B, for service tests. */
public class FakeWorld implements OrganizationRepository, MembershipRepository, ApplicationRepository,
        PaymentRepository, NotificationRepository, EventRepository, UserRepository {

    public static final Set<String> INTERESTS = Set.of("Technology", "Advocacy", "Academic", "Communication", "Design");

    public record Note(String recipientId, NotificationType type, String message) {}

    private static final class Org {
        UUID id;
        String name;
        String category;
        String description = "";
        String logoUrl;
        String coverUrl;
        String email;
        String phone;
        boolean requiresApproval;
        BigDecimal fee = BigDecimal.ZERO;
        List<String> interests = new ArrayList<>();
        List<SocialLink> links = new ArrayList<>();
        UUID roomId = UUID.randomUUID();
    }

    private final Map<UUID, Org> orgs = new LinkedHashMap<>();
    private final Map<UUID, Map<String, LocalDate>> memberships = new HashMap<>();
    private final List<Rows.Application> applications = new ArrayList<>();
    private final List<Rows.Payment> payments = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private final List<Map.Entry<UUID, Rows.Event>> events = new ArrayList<>();
    private final Map<UUID, Integer> certificates = new HashMap<>();
    private final Map<String, UserAccount> accounts = new HashMap<>();

    // ---------- setup helpers for tests ----------

    public UUID addOrg(String name, String category, boolean requiresApproval, String fee) {
        Org o = new Org();
        o.id = UUID.randomUUID();
        o.name = name;
        o.category = category;
        o.requiresApproval = requiresApproval;
        o.fee = new BigDecimal(fee);
        orgs.put(o.id, o);
        return o.id;
    }

    public AuthenticatedUser addStudent(String id, String fullName) {
        UserAccount a = new UserAccount(id, fullName, fullName.split(" ")[0], id + "@g.batstate-u.edu.ph",
                "hash", Role.STUDENT, Set.of());
        accounts.put(id, a);
        return AuthenticatedUser.from(a);
    }

    public AuthenticatedUser addStaff(String id, Role role, UUID... managed) {
        UserAccount a = new UserAccount(id, "Staff " + id, "Staff", id.toLowerCase() + "@batstate-u.edu.ph",
                "hash", role, Set.of(managed));
        accounts.put(id, a);
        return AuthenticatedUser.from(a);
    }

    public void addEvent(UUID orgId, String title, LocalDate date) {
        events.add(Map.entry(orgId, new Rows.Event(UUID.randomUUID(), title, date)));
    }

    public void setCertificates(UUID orgId, int count) {
        certificates.put(orgId, count);
    }

    public List<Note> notifications() {
        return List.copyOf(notes);
    }

    public List<String> messagesFor(String recipientId) {
        return notes.stream().filter(n -> n.recipientId().equals(recipientId)).map(Note::message).toList();
    }

    public List<Rows.Payment> allPayments() {
        return List.copyOf(payments);
    }

    public UUID roomIdOf(UUID orgId) {
        return orgs.get(orgId).roomId;
    }

    public boolean orgExists(UUID orgId) {
        return orgs.containsKey(orgId);
    }

    private String studentName(String id) {
        UserAccount a = accounts.get(id);
        return a == null ? id : a.fullName();
    }

    private int memberCount(UUID orgId) {
        return memberships.getOrDefault(orgId, Map.of()).size();
    }

    // ---------- OrganizationRepository ----------

    @Override
    public List<OrgSummary> searchOrganizations(String text, String category, Set<UUID> onlyOrgIds) {
        return orgs.values().stream()
                .filter(o -> onlyOrgIds == null || onlyOrgIds.contains(o.id))
                .filter(o -> text == null || o.name.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)))
                .filter(o -> category == null || o.category.equals(category))
                .sorted(Comparator.comparing((Org o) -> o.name))
                .map(o -> new OrgSummary(o.id, o.name, o.category, o.logoUrl, o.coverUrl, o.requiresApproval,
                        o.fee, memberCount(o.id)))
                .toList();
    }

    @Override
    public Optional<Organization> findOrganization(UUID orgId) {
        Org o = orgs.get(orgId);
        if (o == null) {
            return Optional.empty();
        }
        return Optional.of(new Organization(o.id, o.name, o.category, o.description, o.logoUrl, o.coverUrl,
                o.email, o.phone, o.requiresApproval, o.fee, List.copyOf(o.interests), List.copyOf(o.links),
                memberCount(o.id)));
    }

    @Override
    public UUID insertOrganization(OrgInput in) {
        UUID id = addOrg(in.name(), in.category(), in.requiresApproval(), in.membershipFee().toPlainString());
        copyInto(orgs.get(id), in);
        return id;
    }

    @Override
    public void updateOrganization(UUID orgId, OrgInput in) {
        Org o = orgs.get(orgId);
        o.name = in.name();
        o.category = in.category();
        o.requiresApproval = in.requiresApproval();
        o.fee = in.membershipFee();
        copyInto(o, in);
    }

    private static void copyInto(Org o, OrgInput in) {
        o.description = in.description();
        o.email = in.contactEmail();
        o.phone = in.contactPhone();
        o.interests = new ArrayList<>(in.interests());
        o.links = new ArrayList<>(in.socialLinks());
    }

    @Override
    public boolean organizationNameTaken(String name, UUID exceptOrgId) {
        return orgs.values().stream().anyMatch(o -> o.name.equalsIgnoreCase(name) && !o.id.equals(exceptOrgId));
    }

    @Override
    public Set<String> allowedInterests() {
        return INTERESTS;
    }

    @Override
    public Optional<UUID> organizationRoomId(UUID orgId) {
        Org o = orgs.get(orgId);
        return o == null ? Optional.empty() : Optional.of(o.roomId);
    }

    @Override
    public Rows.RemovalImpact removalImpact(UUID orgId) {
        return new Rows.RemovalImpact(
                memberCount(orgId),
                (int) events.stream().filter(e -> e.getKey().equals(orgId)).count(),
                certificates.getOrDefault(orgId, 0),
                (int) applications.stream().filter(a -> a.orgId().equals(orgId)).count(),
                (int) payments.stream().filter(p -> p.orgId().equals(orgId)).count());
    }

    @Override
    public void deleteOrganization(UUID orgId) {
        orgs.remove(orgId);
        memberships.remove(orgId);
        applications.removeIf(a -> a.orgId().equals(orgId));
        payments.removeIf(p -> p.orgId().equals(orgId));
        events.removeIf(e -> e.getKey().equals(orgId));
    }

    // ---------- MembershipRepository ----------

    @Override
    public boolean isMember(String studentId, UUID orgId) {
        return memberships.getOrDefault(orgId, Map.of()).containsKey(studentId);
    }

    @Override
    public Set<UUID> memberOrgIds(String studentId) {
        Set<UUID> ids = new java.util.HashSet<>();
        memberships.forEach((orgId, m) -> {
            if (m.containsKey(studentId)) {
                ids.add(orgId);
            }
        });
        return ids;
    }

    @Override
    public void addMember(UUID orgId, String studentId, LocalDate joinedOn) {
        memberships.computeIfAbsent(orgId, k -> new LinkedHashMap<>()).put(studentId, joinedOn);
    }

    @Override
    public boolean removeMember(UUID orgId, String studentId) {
        Map<String, LocalDate> m = memberships.get(orgId);
        return m != null && m.remove(studentId) != null;
    }

    @Override
    public List<Rows.Member> listMembers(UUID orgId, String search) {
        return memberships.getOrDefault(orgId, Map.of()).entrySet().stream()
                .map(e -> new Rows.Member(e.getKey(), studentName(e.getKey()), e.getValue()))
                .filter(m -> search == null
                        || m.fullName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))
                        || m.studentId().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(Rows.Member::fullName))
                .toList();
    }

    // ---------- ApplicationRepository ----------

    @Override
    public boolean hasPendingApplication(String studentId, UUID orgId) {
        return applications.stream().anyMatch(a -> a.studentId().equals(studentId) && a.orgId().equals(orgId)
                && a.status() == ApplicationStatus.PENDING);
    }

    @Override
    public UUID insertApplication(String studentId, UUID orgId, LocalDate dateApplied) {
        UUID id = UUID.randomUUID();
        applications.add(new Rows.Application(id, studentId, studentName(studentId), orgId,
                orgs.get(orgId).name, dateApplied, ApplicationStatus.PENDING));
        return id;
    }

    @Override
    public Optional<Rows.Application> findApplication(UUID applicationId) {
        return applications.stream().filter(a -> a.applicationId().equals(applicationId)).findFirst();
    }

    @Override
    public boolean decideApplication(UUID applicationId, ApplicationStatus newStatus) {
        for (int i = 0; i < applications.size(); i++) {
            Rows.Application a = applications.get(i);
            if (a.applicationId().equals(applicationId) && a.status() == ApplicationStatus.PENDING) {
                applications.set(i, new Rows.Application(a.applicationId(), a.studentId(), a.studentName(),
                        a.orgId(), a.organizationName(), a.dateApplied(), newStatus));
                return true;
            }
        }
        return false;
    }

    private List<Rows.Application> filtered(ApplicationStatus status, Set<UUID> onlyOrgIds, UUID orgFilter) {
        return applications.stream()
                .filter(a -> status == null || a.status() == status)
                .filter(a -> onlyOrgIds == null || onlyOrgIds.contains(a.orgId()))
                .filter(a -> orgFilter == null || a.orgId().equals(orgFilter))
                .toList();
    }

    @Override
    public List<Rows.Application> listApplications(ApplicationStatus status, Set<UUID> onlyOrgIds, UUID orgFilter) {
        return filtered(status, onlyOrgIds, orgFilter);
    }

    @Override
    public Map<ApplicationStatus, Integer> countApplications(Set<UUID> onlyOrgIds, UUID orgFilter) {
        Map<ApplicationStatus, Integer> counts = new EnumMap<>(ApplicationStatus.class);
        for (Rows.Application a : filtered(null, onlyOrgIds, orgFilter)) {
            counts.merge(a.status(), 1, Integer::sum);
        }
        return counts;
    }

    @Override
    public List<Rows.Application> listApplicationsOf(String studentId) {
        return applications.stream()
                .filter(a -> a.studentId().equals(studentId))
                .sorted(Comparator.comparing(Rows.Application::dateApplied).reversed())
                .toList();
    }

    // ---------- PaymentRepository ----------

    @Override
    public boolean hasPaymentRecord(String studentId, UUID orgId) {
        return payments.stream().anyMatch(p -> p.studentId().equals(studentId) && p.orgId().equals(orgId));
    }

    @Override
    public void insertPayment(String studentId, UUID orgId, BigDecimal amount, LocalDate datePaid, PaymentStatus status) {
        payments.add(new Rows.Payment(UUID.randomUUID(), studentId, studentName(studentId), orgId,
                orgs.get(orgId).name, amount, datePaid, status));
    }

    @Override
    public Optional<UUID> findUnpaidPaymentId(String studentId, UUID orgId) {
        return payments.stream()
                .filter(p -> p.studentId().equals(studentId) && p.orgId().equals(orgId)
                        && p.status() == PaymentStatus.UNPAID)
                .map(Rows.Payment::paymentId)
                .findFirst();
    }

    @Override
    public Optional<Rows.Payment> findPayment(UUID paymentId) {
        return payments.stream().filter(p -> p.paymentId().equals(paymentId)).findFirst();
    }

    @Override
    public boolean settlePayment(UUID paymentId, BigDecimal amount, LocalDate datePaid) {
        for (int i = 0; i < payments.size(); i++) {
            Rows.Payment p = payments.get(i);
            if (p.paymentId().equals(paymentId) && p.status() == PaymentStatus.UNPAID) {
                payments.set(i, new Rows.Payment(p.paymentId(), p.studentId(), p.studentName(), p.orgId(),
                        p.organizationName(), amount, datePaid, PaymentStatus.PAID));
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Rows.Payment> listPayments(Set<UUID> onlyOrgIds, UUID orgFilter) {
        return payments.stream()
                .filter(p -> onlyOrgIds == null || onlyOrgIds.contains(p.orgId()))
                .filter(p -> orgFilter == null || p.orgId().equals(orgFilter))
                .toList();
    }

    @Override
    public Map<String, FeeStatus> feeStatusByStudent(UUID orgId) {
        Map<String, FeeStatus> result = new HashMap<>();
        for (Rows.Payment p : payments) {
            if (p.orgId().equals(orgId)) {
                FeeStatus now = p.status() == PaymentStatus.UNPAID ? FeeStatus.UNPAID : FeeStatus.PAID;
                result.merge(p.studentId(), now, (a, b) -> a == FeeStatus.UNPAID || b == FeeStatus.UNPAID
                        ? FeeStatus.UNPAID : FeeStatus.PAID);
            }
        }
        return result;
    }

    @Override
    public FeeStatus feeStatusOf(String studentId, UUID orgId) {
        return feeStatusByStudent(orgId).getOrDefault(studentId, FeeStatus.NONE);
    }

    // ---------- NotificationRepository, EventRepository ----------

    @Override
    public void insertNotification(String recipientId, NotificationType type, String message) {
        notes.add(new Note(recipientId, type, message));
    }

    @Override
    public List<Rows.Event> upcomingEvents(UUID orgId, LocalDate from) {
        return events.stream()
                .filter(e -> e.getKey().equals(orgId) && !e.getValue().eventDate().isBefore(from))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(Rows.Event::eventDate))
                .toList();
    }

    // ---------- UserRepository ----------

    @Override
    public Optional<UserAccount> findById(String userId) {
        return Optional.ofNullable(accounts.get(userId));
    }

    @Override
    public List<String> adminIdsManaging(UUID orgId, Role role) {
        return accounts.values().stream()
                .filter(a -> a.role() == role && a.managedOrgIds().contains(orgId))
                .map(UserAccount::userId)
                .sorted()
                .toList();
    }
}
