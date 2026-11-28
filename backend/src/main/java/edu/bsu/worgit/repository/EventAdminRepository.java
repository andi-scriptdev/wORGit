package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import edu.bsu.worgit.domain.EventRole;
import edu.bsu.worgit.domain.EventRows;

/** Events and their participants (AD-09 to AD-11) and the student's view of them (ST-01, ST-06). */
public interface EventAdminRepository {

    /** Latest date first. scope null = every organization (empty = none); orgFilter null = no filter. */
    List<EventRows.EventListRow> listEvents(Set<UUID> scope, UUID orgFilter);

    Optional<EventRows.EventInfo> findEvent(UUID eventId);

    UUID insertEvent(UUID orgId, String title, LocalDate eventDate, String description);

    /** Participants and certificates go with it (ON DELETE CASCADE). */
    void deleteEvent(UUID eventId);

    /** Ordered by name. */
    List<EventRows.Participant> participants(UUID eventId);

    /** DuplicateKeyException when the student is already a participant. */
    void addParticipant(UUID eventId, String studentId, EventRole role);

    boolean removeParticipant(UUID eventId, String studentId);

    void setAttended(UUID eventId, String studentId, boolean attended);

    /** Attended events of any organization and any date (achievement milestones). */
    int attendedEventCount(String studentId);

    /** Every event the student was added to, latest first. */
    List<EventRows.JoinedEvent> joinedEvents(String studentId);

    /** Events on or after the day in the student's current organizations, soonest first. */
    List<EventRows.UpcomingEvent> upcomingEventsOfMember(String studentId, LocalDate from, int limit);
}
