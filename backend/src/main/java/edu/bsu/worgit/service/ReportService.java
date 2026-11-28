package edu.bsu.worgit.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.bsu.worgit.domain.Achievements;
import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.EventRows;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Rules;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.AchievementRepository;
import edu.bsu.worgit.repository.CertificateRepository;
import edu.bsu.worgit.repository.EventAdminRepository;
import edu.bsu.worgit.repository.MembershipRepository;
import edu.bsu.worgit.repository.NotificationRepository;
import edu.bsu.worgit.repository.OrganizationRepository;
import edu.bsu.worgit.repository.ProfileRepository;
import edu.bsu.worgit.repository.StatsRepository;

/** ST-01 Dashboard and ST-06 My Engagement Report. */
@Service
public class ReportService {

    static final int UPDATES = 3;          // spec section 6: latest 3
    static final int UPCOMING_EVENTS = 3;  // decision 11

    private final StatsRepository stats;
    private final EventAdminRepository events;
    private final AchievementRepository achievements;
    private final CertificateRepository certificates;
    private final NotificationRepository notifications;
    private final OrganizationRepository orgs;
    private final MembershipRepository members;
    private final ProfileRepository profiles;
    private final Clock clock;

    public ReportService(StatsRepository stats, EventAdminRepository events, AchievementRepository achievements,
                         CertificateRepository certificates, NotificationRepository notifications,
                         OrganizationRepository orgs, MembershipRepository members, ProfileRepository profiles,
                         Clock clock) {
        this.stats = stats;
        this.events = events;
        this.achievements = achievements;
        this.certificates = certificates;
        this.notifications = notifications;
        this.orgs = orgs;
        this.members = members;
        this.profiles = profiles;
        this.clock = clock;
    }

    public EventViews.StudentDashboard dashboard(AuthenticatedUser student) {
        Permissions.requireStudent(student);
        String id = student.userId();
        LocalDate today = LocalDate.now(clock);
        int attended = events.attendedEventCount(id);
        EventViews.NextBadge next = Achievements.next(attended)
                .map(m -> new EventViews.NextBadge(m.title(), m.threshold(), attended, Achievements.progress(attended, m)))
                .orElse(null);
        List<PeopleViews.NotificationView> updates = notifications.listNotifications(id, UPDATES).stream()
                .map(n -> new PeopleViews.NotificationView(n.notificationId(), n.type(), n.message(), n.createdAt(),
                        n.read()))
                .toList();
        List<EventViews.MyOrganization> mine = orgs.searchOrganizations(null, null, members.memberOrgIds(id)).stream()
                .map(o -> new EventViews.MyOrganization(o.orgId(), o.name(), o.memberCount()))
                .toList();
        return new EventViews.StudentDashboard(student.displayName(), statsOf(id), notifications.unreadCount(id),
                updates, next, events.upcomingEventsOfMember(id, today, UPCOMING_EVENTS), mine);
    }

    public EventViews.EngagementReport report(AuthenticatedUser student) {
        Permissions.requireStudent(student);
        String id = student.userId();
        String fullName = profiles.findStudentProfile(id).map(p -> p.fullName()).orElseThrow(NotFoundException::new);
        Map<String, LocalDate> earned = achievements.achievementsOf(id).stream()
                .collect(Collectors.toMap(EventRows.EarnedAchievement::title, EventRows.EarnedAchievement::dateEarned,
                        (a, b) -> a));
        List<EventViews.AchievementView> badges = Achievements.MILESTONES.stream()
                .map(m -> new EventViews.AchievementView(m.title(), m.threshold(), earned.containsKey(m.title()),
                        earned.get(m.title())))
                .toList();
        List<EventViews.CertificateRowView> certs = certificates.certificatesOf(id).stream()
                .map(c -> new EventViews.CertificateRowView(c.certificateId(), c.eventTitle(),
                        c.toCertificate().getShortType(), c.dateIssued()))
                .toList();
        return new EventViews.EngagementReport(id, fullName, statsOf(id), badges, events.joinedEvents(id), certs);
    }

    /**
     * Score = orgs x 10 + messages x 2 + peers x 5. Participation = attended / joined events x 100, counting
     * only events dated today or earlier (decision 10); null ("-") when there are none.
     */
    EventViews.Stats statsOf(String studentId) {
        return statsOf(stats.studentStats(studentId, LocalDate.now(clock)));
    }

    static EventViews.Stats statsOf(EventRows.StudentStats s) {
        OptionalInt rate = Rules.participationRate(s.attendedEvents(), s.joinedEvents());
        return new EventViews.Stats(s.organizations(), s.messages(), s.peers(),
                Rules.engagementScore(s.organizations(), s.messages(), s.peers()),
                rate.isPresent() ? Integer.valueOf(rate.getAsInt()) : null);
    }
}
