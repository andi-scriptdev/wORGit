package edu.bsu.worgit.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Read models of slice 1D (events, certificates, achievements, statistics) returned by the repositories. */
public final class EventRows {
    private EventRows() {}

    /** One row of AD-09: People = participants, Certificates = issued certificates. */
    public record EventListRow(UUID eventId, String title, UUID orgId, String organizationName, LocalDate eventDate,
                               int people, int certificates) {}

    public record EventInfo(UUID eventId, String title, UUID orgId, String organizationName, LocalDate eventDate,
                            String description) {}

    /** A participant of AD-11 with the certificate they hold for this event, if any (both null otherwise). */
    public record Participant(String studentId, String fullName, EventRole role, boolean attended,
                              String certificateId, CertificateType certificateType) {}

    /** A stored certificate with what ST-07 prints (organization name looked up from the event). */
    public record CertificateRow(String certificateId, String studentId, String studentName, UUID eventId,
                                 String eventTitle, UUID orgId, String organizationName, LocalDate dateIssued,
                                 CertificateType type) {

        public Certificate toCertificate() {
            return Certificate.of(type, certificateId, studentId, studentName, eventId, eventTitle, organizationName,
                    dateIssued);
        }
    }

    public record EarnedAchievement(String title, LocalDate dateEarned) {}

    /** ST-06 section 3: every event the student was added to. */
    public record JoinedEvent(UUID eventId, String title, String organizationName, LocalDate eventDate,
                              EventRole role, boolean attended) {}

    /** ST-01 Upcoming events. */
    public record UpcomingEvent(UUID eventId, String title, UUID orgId, String organizationName, LocalDate eventDate) {}

    /**
     * The numbers behind the score and the participation rate. joinedEvents and attendedEvents only count
     * events dated on or before the given day (decision 10).
     */
    public record StudentStats(int organizations, int messages, int peers, int joinedEvents, int attendedEvents) {}

    public record StudentRef(String studentId, String fullName, String displayName) {}

    public record OrgCount(UUID orgId, String name, int members) {}

    /** System Admin tiles (p25). */
    public record CampusTotals(int students, int organizations, int messages, int connections) {}
}
