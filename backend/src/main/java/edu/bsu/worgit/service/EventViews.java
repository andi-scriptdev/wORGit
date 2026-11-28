package edu.bsu.worgit.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import edu.bsu.worgit.domain.EventRole;
import edu.bsu.worgit.domain.EventRows;

/** What the app receives for slice 1D (sent as JSON; null fields are left out). */
public final class EventViews {
    private EventViews() {}

    // ---------- AD-09 to AD-11 ----------

    /** AD-10 form. description may be empty. */
    public record EventInput(UUID orgId, String title, LocalDate eventDate, String description) {}

    public record CreatedEvent(UUID eventId) {}

    /** role null = ATTENDEE (the form's default chip "Role: Attendee"). */
    public record ParticipantInput(String studentId, EventRole role) {}

    public record AttendanceEntry(String studentId, boolean attended) {}

    /** "Save attendance": the Yes/No of every row that changed (rows not sent stay as they are). */
    public record AttendanceInput(List<AttendanceEntry> attendance) {}

    /** Certificates tab state of one participant. */
    public enum CertificateState { ISSUED, CAN_GENERATE, NOT_ELIGIBLE }

    public record ParticipantView(String studentId, String fullName, EventRole role, boolean attended,
                                  CertificateState certificateState, String certificateId, String certificateType) {}

    public record MemberOption(String studentId, String fullName) {}

    /**
     * AD-11. addableMembers: the organization's members who are not participants yet (the "Member" list).
     * canDelete / deleteUntil: deleting is allowed up to 3 days before the event date.
     */
    public record EventDetail(UUID eventId, String title, UUID orgId, String organizationName, LocalDate eventDate,
                              String description, List<ParticipantView> participants,
                              List<MemberOption> addableMembers, boolean canDelete, LocalDate deleteUntil) {}

    /** ST-07 and the staff "View": everything the printable certificate shows. */
    public record CertificateView(String certificateId, String title, String studentName, String text,
                                  String roleLine, String eventTitle, String organizationName, LocalDate dateIssued,
                                  String type) {}

    /** "Generate for all attendees": created now, already issued before, and the names that are not eligible. */
    public record GenerationResult(List<CertificateView> created, int alreadyIssued, List<String> notEligible) {}

    // ---------- statistics ----------

    /** participationRate null = no events yet (the app shows "-"). */
    public record Stats(int organizations, int messages, int peers, int score, Integer participationRate) {}

    // ---------- ST-01 ----------

    public record NextBadge(String title, int threshold, int attended, String progress) {}

    public record MyOrganization(UUID orgId, String name, int memberCount) {}

    /** updates: the latest 3 notifications (View all opens ST-09). nextBadge null once every badge is earned. */
    public record StudentDashboard(String displayName, Stats stats, int unreadNotifications,
                                   List<PeopleViews.NotificationView> updates, NextBadge nextBadge,
                                   List<EventRows.UpcomingEvent> upcomingEvents, List<MyOrganization> myOrganizations) {}

    // ---------- ST-06 ----------

    public record AchievementView(String title, int threshold, boolean earned, LocalDate dateEarned) {}

    public record CertificateRowView(String certificateId, String eventTitle, String type, LocalDate dateIssued) {}

    public record EngagementReport(String studentId, String fullName, Stats stats, List<AchievementView> achievements,
                                   List<EventRows.JoinedEvent> events, List<CertificateRowView> certificates) {}

    // ---------- AD-05 ----------

    public record StudentSummary(String studentId, String fullName, String displayName, Stats stats,
                                 List<String> interests) {}

    // ---------- AD-01 ----------

    public record NeedsAttention(int pendingApplications, int unpaidFees, int eventsThisWeek) {}

    public record OrgTiles(int members, int pendingApplications, int unpaidFees, int eventsThisWeek) {}

    /**
     * scope ALL (System Administrator: totals + needsAttention) or MANAGED (Facilitator, Adviser:
     * organizations to choose from, the selected one and its tiles). membersPerOrganization is always set.
     */
    public record AdminDashboard(String scope, EventRows.CampusTotals totals, NeedsAttention needsAttention,
                                 List<PeopleViews.OrgRef> organizations, UUID selectedOrgId, OrgTiles tiles,
                                 List<EventRows.OrgCount> membersPerOrganization, boolean readOnly) {}
}
