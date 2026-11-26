package edu.bsu.worgit.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Small read models returned by repositories (and sent to the app as they are). */
public final class Rows {
    private Rows() {}

    public record Member(String studentId, String fullName, LocalDate joinedOn) {}

    public record Application(UUID applicationId, String studentId, String studentName, UUID orgId,
                              String organizationName, LocalDate dateApplied, ApplicationStatus status) {}

    public record Payment(UUID paymentId, String studentId, String studentName, UUID orgId,
                          String organizationName, BigDecimal amount, LocalDate datePaid, PaymentStatus status) {}

    public record Event(UUID eventId, String title, LocalDate eventDate) {}

    public record RemovalImpact(int members, int events, int certificates, int applications, int payments) {}
}
