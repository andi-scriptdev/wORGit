package edu.bsu.worgit.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.Achievements;
import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.CertificateType;
import edu.bsu.worgit.domain.EventRole;
import edu.bsu.worgit.domain.EventRows;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Rules;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.AchievementRepository;
import edu.bsu.worgit.repository.EventAdminRepository;
import edu.bsu.worgit.repository.MembershipRepository;
import edu.bsu.worgit.repository.NotificationRepository;
import edu.bsu.worgit.repository.OrganizationRepository;
import edu.bsu.worgit.repository.UserRepository;

/**
 * AD-09 Events, AD-10 Create event, AD-11 Event detail (participants and attendance). System Administrator:
 * every organization; Facilitator: own organizations; Advisers and students have no access. Saving attendance
 * awards the First Event / 5 / 10 milestones and notifies the student.
 */
@Service
public class EventService {

    /** Your rule: an event can be deleted up to this many days before its date. */
    static final int DELETE_DAYS_BEFORE = 3;

    private final EventAdminRepository events;
    private final OrganizationRepository orgs;
    private final MembershipRepository members;
    private final AchievementRepository achievements;
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final Clock clock;

    public EventService(EventAdminRepository events, OrganizationRepository orgs, MembershipRepository members,
                        AchievementRepository achievements, NotificationRepository notifications,
                        UserRepository users, Clock clock) {
        this.events = events;
        this.orgs = orgs;
        this.members = members;
        this.achievements = achievements;
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    public List<EventRows.EventListRow> list(AuthenticatedUser actor, UUID orgId) {
        Permissions.requireGlobal(actor, Capability.MANAGE_EVENTS);
        if (orgId != null) {
            Permissions.requireForOrg(actor, Capability.MANAGE_EVENTS, orgId);
        }
        return events.listEvents(Permissions.visibleOrgIds(actor), orgId);
    }

    /** Title required, title and description at most 250 characters, any date (past dates allowed). */
    @Transactional
    public UUID create(AuthenticatedUser actor, EventViews.EventInput in) {
        if (in == null || in.orgId() == null) {
            throw new InvalidInputException(Messages.INVALID_REQUEST, "orgId");
        }
        Permissions.requireForOrg(actor, Capability.MANAGE_EVENTS, in.orgId());
        orgs.findOrganization(in.orgId()).orElseThrow(NotFoundException::new);
        String title = in.title() == null ? "" : in.title().strip();
        if (title.isEmpty()) {
            throw new InvalidInputException(Messages.TITLE_REQUIRED, "title");
        }
        Rules.requireOrgText("Title", "title", title);
        if (in.eventDate() == null) {
            throw new InvalidInputException(Messages.DATE_REQUIRED, "eventDate");
        }
        String description = Rules.requireOrgText("Description", "description",
                in.description() == null ? "" : in.description().strip());
        return events.insertEvent(in.orgId(), title, in.eventDate(), description);
    }

    public EventViews.EventDetail detail(AuthenticatedUser actor, UUID eventId) {
        EventRows.EventInfo event = requireManaged(actor, eventId);
        List<EventRows.Participant> participants = events.participants(eventId);
        Set<String> already = participants.stream().map(EventRows.Participant::studentId).collect(Collectors.toSet());
        List<EventViews.MemberOption> addable = members.listMembers(event.orgId(), null).stream()
                .filter(m -> !already.contains(m.studentId()))
                .map(m -> new EventViews.MemberOption(m.studentId(), m.fullName()))
                .toList();
        LocalDate deleteUntil = event.eventDate().minusDays(DELETE_DAYS_BEFORE);
        return new EventViews.EventDetail(event.eventId(), event.title(), event.orgId(), event.organizationName(),
                event.eventDate(), event.description(),
                participants.stream().map(EventService::participantView).toList(),
                addable, !LocalDate.now(clock).isAfter(deleteUntil), deleteUntil);
    }

    /** Only current members of the event's organization (decision 1). */
    @Transactional
    public EventViews.EventDetail addParticipant(AuthenticatedUser actor, UUID eventId, EventViews.ParticipantInput in) {
        EventRows.EventInfo event = requireManaged(actor, eventId);
        if (in == null || in.studentId() == null || in.studentId().isBlank()) {
            throw new InvalidInputException(Messages.INVALID_REQUEST, "studentId");
        }
        String studentId = in.studentId().strip();
        String name = nameOf(studentId);
        if (!members.isMember(studentId, event.orgId())) {
            throw new InvalidInputException(Messages.studentNotMember(name, event.organizationName()), "studentId");
        }
        try {
            events.addParticipant(eventId, studentId, in.role() == null ? EventRole.ATTENDEE : in.role());
        } catch (DuplicateKeyException e) {
            throw new InvalidInputException(Messages.alreadyParticipant(name), "studentId");
        }
        return detail(actor, eventId);
    }

    /** Refused for someone who already has a certificate for this event (decision 2). */
    @Transactional
    public EventViews.EventDetail removeParticipant(AuthenticatedUser actor, UUID eventId, String studentId) {
        requireManaged(actor, eventId);
        EventRows.Participant p = participantOrNull(eventId, studentId);
        if (p == null) {
            throw new NotFoundException();
        }
        if (p.certificateId() != null) {
            throw new InvalidInputException(Messages.hasCertificate(p.fullName()));
        }
        events.removeParticipant(eventId, studentId);
        return detail(actor, eventId);
    }

    /**
     * Saves the Yes/No of the rows sent. A participant with a certificate cannot go back to No (decision 3).
     * Nothing is saved when any row is invalid. Every student marked Yes is checked for new milestones.
     */
    @Transactional
    public EventViews.EventDetail saveAttendance(AuthenticatedUser actor, UUID eventId, EventViews.AttendanceInput in) {
        requireManaged(actor, eventId);
        Map<String, EventRows.Participant> byId = new HashMap<>();
        for (EventRows.Participant p : events.participants(eventId)) {
            byId.put(p.studentId(), p);
        }
        List<EventViews.AttendanceEntry> entries = in == null || in.attendance() == null ? List.of() : in.attendance();
        for (EventViews.AttendanceEntry entry : entries) {
            EventRows.Participant p = entry == null ? null : byId.get(entry.studentId());
            if (p == null) {
                String id = entry == null ? "?" : entry.studentId();
                throw new InvalidInputException(Messages.notParticipant(nameOf(id)), "attendance");
            }
            if (!entry.attended() && p.certificateId() != null) {
                throw new InvalidInputException(Messages.hasCertificate(p.fullName()), "attendance");
            }
        }
        for (EventViews.AttendanceEntry entry : entries) {
            EventRows.Participant p = byId.get(entry.studentId());
            if (p.attended() != entry.attended()) {
                events.setAttended(eventId, entry.studentId(), entry.attended());
                if (entry.attended()) {
                    awardMilestones(entry.studentId());
                }
            }
        }
        return detail(actor, eventId);
    }

    /** Up to 3 days before the event date (your rule), and never while it has issued certificates. */
    @Transactional
    public void delete(AuthenticatedUser actor, UUID eventId) {
        EventRows.EventInfo event = requireManaged(actor, eventId);
        if (LocalDate.now(clock).isAfter(event.eventDate().minusDays(DELETE_DAYS_BEFORE))) {
            throw new InvalidInputException(Messages.DELETE_TOO_LATE);
        }
        int certificates = (int) events.participants(eventId).stream().filter(p -> p.certificateId() != null).count();
        if (certificates > 0) {
            throw new InvalidInputException(Messages.eventHasCertificates(event.title(), certificates));
        }
        events.deleteEvent(eventId);
    }

    /** Awards every milestone reached and not yet earned; a milestone is never taken back (decision 8). */
    void awardMilestones(String studentId) {
        int attended = events.attendedEventCount(studentId);
        for (Achievements.Milestone m : Achievements.earned(attended)) {
            if (achievements.insertAchievement(studentId, m.title(), LocalDate.now(clock))) {
                notifications.insertNotification(studentId, NotificationType.ACHIEVEMENT,
                        Messages.achievementUnlocked(m.title()));
            }
        }
    }

    /** The event, after checking that the actor manages events of its organization. Public: CertificateService uses it. */
    public EventRows.EventInfo requireManaged(AuthenticatedUser actor, UUID eventId) {
        Permissions.requireGlobal(actor, Capability.MANAGE_EVENTS);
        EventRows.EventInfo event = events.findEvent(eventId).orElseThrow(NotFoundException::new);
        Permissions.requireForOrg(actor, Capability.MANAGE_EVENTS, event.orgId());
        return event;
    }

    public EventRows.Participant participantOrNull(UUID eventId, String studentId) {
        return events.participants(eventId).stream()
                .filter(p -> p.studentId().equals(studentId))
                .findFirst()
                .orElse(null);
    }

    private String nameOf(String studentId) {
        return users.findById(studentId).map(UserAccount::fullName).orElse(studentId);
    }

    static EventViews.ParticipantView participantView(EventRows.Participant p) {
        EventViews.CertificateState state = p.certificateId() != null ? EventViews.CertificateState.ISSUED
                : p.attended() ? EventViews.CertificateState.CAN_GENERATE : EventViews.CertificateState.NOT_ELIGIBLE;
        String type = p.certificateType() == null ? null
                : p.certificateType() == CertificateType.ORGANIZER ? "Appreciation" : "Participation";
        return new EventViews.ParticipantView(p.studentId(), p.fullName(), p.role(), p.attended(), state,
                p.certificateId(), type);
    }
}
