package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.FeeStatus;
import edu.bsu.worgit.domain.OrgInput;
import edu.bsu.worgit.domain.Organization;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.domain.SocialLink;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.support.FakeWorld;
import edu.bsu.worgit.support.TestClock;

class OrganizationServiceTest {

    private FakeWorld w;
    private OrganizationService svc;
    private UUID campus;
    private UUID robotics;
    private UUID web;
    private UUID zeta;
    private AuthenticatedUser andrea;
    private AuthenticatedUser frenzy;
    private AuthenticatedUser charles;
    private AuthenticatedUser adviser;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        campus = w.addOrg("Campus Media Network", "Communication", true, "0");
        robotics = w.addOrg("Robotics Club", "Technology", false, "100");
        web = w.addOrg("Web Developers Guild", "Technology", false, "0");
        zeta = w.addOrg("Zeta Approval", "Design", true, "0");
        andrea = w.addStudent("25-09041", "Andrea Valenzuela");
        frenzy = w.addStaff("ADM-001", Role.ORGANIZATION_FACILITATOR, campus);
        charles = w.addStaff("ADM-002", Role.SYSTEM_ADMINISTRATOR);
        adviser = w.addStaff("ADM-003", Role.FACULTY_ADVISER, campus);
        svc = new OrganizationService(w, w, w, w, w, new TestClock());
    }

    private static OrgInput input(String name) {
        return new OrgInput(name, "Technology", "About us", false, new BigDecimal("0"), null, null,
                List.of(), List.of());
    }

    private static OrgInput withDescription(OrgInput in, String description) {
        return new OrgInput(in.name(), in.category(), description, in.requiresApproval(), in.membershipFee(),
                in.contactEmail(), in.contactPhone(), in.interests(), in.socialLinks());
    }

    // ---------- students ----------

    @Test
    void browseShowsTheStateOfEachCard() {
        w.addMember(web, "25-09041", LocalDate.of(2026, 9, 1));
        w.insertApplication("25-09041", campus, LocalDate.of(2026, 10, 12));
        List<Views.OrgCard> cards = svc.browse(andrea, null, null);
        assertThat(cards).hasSize(4);
        assertThat(cards.get(0).name()).isEqualTo("Campus Media Network");
        assertThat(cards.get(0).state()).isEqualTo(Views.CardState.PENDING);
        assertThat(cards.get(1).state()).isEqualTo(Views.CardState.JOIN);
        assertThat(cards.get(2).state()).isEqualTo(Views.CardState.JOINED);
        assertThat(cards.get(2).memberCount()).isEqualTo(1);
        assertThat(cards.get(3).state()).isEqualTo(Views.CardState.APPLY);
    }

    @Test
    void browseFiltersBySearchTextAndCategory() {
        assertThat(svc.browse(andrea, "guild", null)).hasSize(1);
        assertThat(svc.browse(andrea, null, "Technology")).hasSize(2);
        assertThat(svc.browse(andrea, "  ", "  ")).hasSize(4);
        assertThat(svc.browse(andrea, "nothing like this", null)).isEmpty();
    }

    @Test
    void myOrganizationsListsOnlyJoinedOnes() {
        assertThat(svc.mine(andrea)).isEmpty();
        w.addMember(web, "25-09041", LocalDate.of(2026, 9, 1));
        List<Views.OrgCard> mine = svc.mine(andrea);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).state()).isEqualTo(Views.CardState.JOINED);
    }

    @Test
    void myApplicationsShowsStatusAndDate() {
        w.insertApplication("25-09041", campus, LocalDate.of(2026, 10, 12));
        List<Views.MyApplication> apps = svc.myApplications(andrea);
        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).organizationName()).isEqualTo("Campus Media Network");
        assertThat(apps.get(0).dateApplied()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void detailForANonMemberHasNoRoomOrFee() {
        Views.OrgDetail d = svc.detail(andrea, robotics);
        assertThat(d.name()).isEqualTo("Robotics Club");
        assertThat(d.membership().state()).isEqualTo(Views.CardState.JOIN);
        assertThat(d.membership().feeStatus()).isNull();
        assertThat(d.membership().roomId()).isNull();
    }

    @Test
    void detailForAMemberHasTheRoomFeeAndUpcomingEventsOnly() {
        w.addMember(robotics, "25-09041", LocalDate.of(2026, 9, 1));
        w.insertPayment("25-09041", robotics, new BigDecimal("100"), null, edu.bsu.worgit.domain.PaymentStatus.UNPAID);
        w.addEvent(robotics, "Past hackathon", LocalDate.of(2026, 11, 1));
        w.addEvent(robotics, "Hackathon 2026", LocalDate.of(2026, 12, 12));
        Views.OrgDetail d = svc.detail(andrea, robotics);
        assertThat(d.membership().state()).isEqualTo(Views.CardState.JOINED);
        assertThat(d.membership().feeStatus()).isEqualTo(FeeStatus.UNPAID);
        assertThat(d.membership().roomId()).isEqualTo(w.roomIdOf(robotics));
        assertThat(d.upcomingEvents()).hasSize(1);
        assertThat(d.upcomingEvents().get(0).title()).isEqualTo("Hackathon 2026");
    }

    @Test
    void detailOfAnUnknownOrganizationIsNotFoundAndStaffAreRefused() {
        assertThatThrownBy(() -> svc.detail(andrea, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> svc.detail(charles, web)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.browse(frenzy, null, null)).isInstanceOf(ForbiddenException.class);
    }

    // ---------- staff list and form ----------

    @Test
    void staffListIsScopedAndCarriesTheButtonFlags() {
        List<Views.StaffOrgRow> all = svc.listForStaff(charles, null);
        assertThat(all).hasSize(4);
        assertThat(all.get(0).canEditAllFields()).isTrue();
        assertThat(all.get(0).canRemove()).isTrue();

        List<Views.StaffOrgRow> mine = svc.listForStaff(frenzy, null);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).name()).isEqualTo("Campus Media Network");
        assertThat(mine.get(0).canEditProfile()).isTrue();
        assertThat(mine.get(0).canEditAllFields()).isFalse();
        assertThat(mine.get(0).canRemove()).isFalse();

        List<Views.StaffOrgRow> viewOnly = svc.listForStaff(adviser, null);
        assertThat(viewOnly).hasSize(1);
        assertThat(viewOnly.get(0).canEditProfile()).isFalse();

        assertThatThrownBy(() -> svc.listForStaff(andrea, null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void staffFormDataIsScoped() {
        assertThat(svc.getForStaff(frenzy, campus).name()).isEqualTo("Campus Media Network");
        assertThatThrownBy(() -> svc.getForStaff(frenzy, web)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.getForStaff(charles, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    // ---------- create ----------

    @Test
    void systemAdministratorCreatesAnOrganizationWithInterestsAndLinks() {
        OrgInput in = new OrgInput("  New Club  ", "Design", "Hello", true, new BigDecimal("150"),
                "club@batstate-u.edu.ph", "0917", List.of("Design", "Design", "Technology"),
                List.of(new SocialLink("Facebook", "https://facebook.com/club")));
        UUID id = svc.create(charles, in);
        Organization o = w.findOrganization(id).get();
        assertThat(o.name()).isEqualTo("New Club");
        assertThat(o.requiresApproval()).isTrue();
        assertThat(o.membershipFee().compareTo(new BigDecimal("150"))).isZero();
        assertThat(o.interests()).hasSize(2);
        assertThat(o.socialLinks()).hasSize(1);
    }

    @Test
    void onlyTheSystemAdministratorCreates() {
        assertThatThrownBy(() -> svc.create(frenzy, input("X"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.create(adviser, input("X"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.create(andrea, input("X"))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createValidatesEveryField() {
        assertThatThrownBy(() -> svc.create(charles, input("   ")))
                .isInstanceOf(InvalidInputException.class).hasMessage("Name is required.");
        assertThatThrownBy(() -> svc.create(charles, input("robotics club")))
                .isInstanceOf(InvalidInputException.class).hasMessage("An organization named robotics club already exists.");
        assertThatThrownBy(() -> svc.create(charles, new OrgInput("A", "Sports", "", false, null, null, null, null, null)))
                .isInstanceOf(InvalidInputException.class).hasMessage("Choose a category from the list.");
        assertThatThrownBy(() -> svc.create(charles, new OrgInput("A", "Design", "", false, new BigDecimal("-1"),
                null, null, null, null)))
                .isInstanceOf(InvalidInputException.class).hasMessage("Membership fee cannot be negative.");
        assertThatThrownBy(() -> svc.create(charles, withDescription(input("A"), "d".repeat(251))))
                .isInstanceOf(InvalidInputException.class).hasMessage("Description must be 250 characters or fewer");
        assertThatThrownBy(() -> svc.create(charles, new OrgInput("A", "Design", "", false, null, null, null,
                List.of("Sports"), null)))
                .isInstanceOf(InvalidInputException.class).hasMessage("Choose interests from the list.");
        assertThatThrownBy(() -> svc.create(charles, new OrgInput("A", "Design", "", false, null, null, null, null,
                List.of(new SocialLink("Facebook", "ftp://example.com")))))
                .isInstanceOf(InvalidInputException.class).hasMessage(
                        "Each social link needs a platform and a link starting with http:// or https://");
    }

    // ---------- update ----------

    @Test
    void facilitatorEditsTheProfileContentOfTheirOrganization() {
        Organization current = w.findOrganization(campus).get();
        OrgInput in = new OrgInput(current.name(), current.category(), "New description", true, current.membershipFee(),
                "media@batstate-u.edu.ph", "0917", List.of("Communication"),
                List.of(new SocialLink("Instagram", "https://instagram.com/media")));
        svc.update(frenzy, campus, in);
        Organization o = w.findOrganization(campus).get();
        assertThat(o.description()).isEqualTo("New description");
        assertThat(o.contactEmail()).isEqualTo("media@batstate-u.edu.ph");
        assertThat(o.interests()).hasSize(1);
        assertThat(o.socialLinks()).hasSize(1);
    }

    @Test
    void facilitatorCannotChangeNameCategoryApprovalOrFee() {
        Organization c = w.findOrganization(campus).get();
        OrgInput renamed = new OrgInput("Renamed", c.category(), "", c.requiresApproval(), c.membershipFee(), null, null, null, null);
        OrgInput recategorized = new OrgInput(c.name(), "Design", "", c.requiresApproval(), c.membershipFee(), null, null, null, null);
        OrgInput approvalFlipped = new OrgInput(c.name(), c.category(), "", !c.requiresApproval(), c.membershipFee(), null, null, null, null);
        OrgInput feeChanged = new OrgInput(c.name(), c.category(), "", c.requiresApproval(), new BigDecimal("25"), null, null, null, null);
        assertThatThrownBy(() -> svc.update(frenzy, campus, renamed)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.update(frenzy, campus, recategorized)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.update(frenzy, campus, approvalFlipped)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.update(frenzy, campus, feeChanged)).isInstanceOf(ForbiddenException.class);
        assertThat(w.findOrganization(campus).get().name()).isEqualTo("Campus Media Network");
    }

    @Test
    void facilitatorCannotEditOtherOrganizationsAndAdvisersCannotEditAtAll() {
        assertThatThrownBy(() -> svc.update(frenzy, web, input("Web Developers Guild"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.update(adviser, campus, input("Campus Media Network"))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void systemAdministratorChangesEveryFieldButNotIntoAnExistingName() {
        svc.update(charles, web, new OrgInput("Web Guild", "Design", "x", true, new BigDecimal("75"), null, null, null, null));
        Organization o = w.findOrganization(web).get();
        assertThat(o.name()).isEqualTo("Web Guild");
        assertThat(o.category()).isEqualTo("Design");
        assertThat(o.requiresApproval()).isTrue();
        assertThat(o.membershipFee().compareTo(new BigDecimal("75"))).isZero();

        assertThatThrownBy(() -> svc.update(charles, web, input("Robotics Club")))
                .isInstanceOf(InvalidInputException.class);
        svc.update(charles, web, input("Web Guild"));   // keeping its own name is fine
    }

    @Test
    void aMissingFeeMeansUnchanged() {
        svc.update(charles, robotics, new OrgInput("Robotics Club", "Technology", "", false, null, null, null, null, null));
        assertThat(w.findOrganization(robotics).get().membershipFee().compareTo(new BigDecimal("100"))).isZero();
    }

    // ---------- remove ----------

    @Test
    void removalImpactCountsWhatWouldBeDeleted() {
        w.addMember(web, "25-09041", LocalDate.of(2026, 9, 1));
        w.addEvent(web, "Event", LocalDate.of(2026, 12, 1));
        w.setCertificates(web, 2);
        Rows.RemovalImpact impact = svc.removalImpact(charles, web);
        assertThat(impact.members()).isEqualTo(1);
        assertThat(impact.events()).isEqualTo(1);
        assertThat(impact.certificates()).isEqualTo(2);
    }

    @Test
    void anOrganizationWithCertificatesCannotBeRemoved() {
        w.setCertificates(web, 2);
        assertThatThrownBy(() -> svc.remove(charles, web))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Cannot remove Web Developers Guild: 2 issued certificates would be deleted.");
        assertThat(w.orgExists(web)).isTrue();
    }

    @Test
    void anOrganizationWithoutCertificatesIsRemoved() {
        svc.remove(charles, web);
        assertThat(w.orgExists(web)).isFalse();
    }

    @Test
    void onlyTheSystemAdministratorRemoves() {
        assertThatThrownBy(() -> svc.remove(frenzy, campus)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.remove(adviser, campus)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.removalImpact(frenzy, campus)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.remove(charles, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
