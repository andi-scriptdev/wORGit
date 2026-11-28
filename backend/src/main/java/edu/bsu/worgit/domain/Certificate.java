package edu.bsu.worgit.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Class diagram: abstract Certificate with ParticipationCertificate and OrganizerCertificate.
 * The wording is from the spec (section 7) and wireframes p22 and p23.
 */
public abstract sealed class Certificate permits Certificate.Participation, Certificate.Organizer {

    private final String certificateId;
    private final String studentId;
    private final String studentName;
    private final UUID eventId;
    private final String eventTitle;
    private final String organizationName;
    private final LocalDate dateIssued;

    protected Certificate(String certificateId, String studentId, String studentName, UUID eventId,
                          String eventTitle, String organizationName, LocalDate dateIssued) {
        this.certificateId = certificateId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.organizationName = organizationName;
        this.dateIssued = dateIssued;
    }

    public static Certificate of(CertificateType type, String certificateId, String studentId, String studentName,
                                 UUID eventId, String eventTitle, String organizationName, LocalDate dateIssued) {
        return type == CertificateType.ORGANIZER
                ? new Organizer(certificateId, studentId, studentName, eventId, eventTitle, organizationName, dateIssued)
                : new Participation(certificateId, studentId, studentName, eventId, eventTitle, organizationName, dateIssued);
    }

    public abstract CertificateType type();

    /** "Certificate of Participation" or "Certificate of Appreciation". */
    public abstract String getTitle();

    /** The line under the student's name. */
    public abstract String getCertificateText();

    /** "Role: Event Organizer" on the Appreciation certificate, otherwise null. */
    public abstract String getRoleLine();

    /** "Participation" or "Appreciation", as in the report and certificate tables. */
    public abstract String getShortType();

    public String certificateId() { return certificateId; }
    public String studentId() { return studentId; }
    public String studentName() { return studentName; }
    public UUID eventId() { return eventId; }
    public String eventTitle() { return eventTitle; }
    public String organizationName() { return organizationName; }
    public LocalDate dateIssued() { return dateIssued; }

    public static final class Participation extends Certificate {
        Participation(String id, String studentId, String studentName, UUID eventId, String eventTitle,
                      String orgName, LocalDate issued) {
            super(id, studentId, studentName, eventId, eventTitle, orgName, issued);
        }

        @Override public CertificateType type() { return CertificateType.PARTICIPATION; }
        @Override public String getTitle() { return "Certificate of Participation"; }
        @Override public String getCertificateText() {
            return "attended " + eventTitle() + ", organized by " + organizationName();
        }
        @Override public String getRoleLine() { return null; }
        @Override public String getShortType() { return "Participation"; }
    }

    public static final class Organizer extends Certificate {
        Organizer(String id, String studentId, String studentName, UUID eventId, String eventTitle,
                  String orgName, LocalDate issued) {
            super(id, studentId, studentName, eventId, eventTitle, orgName, issued);
        }

        @Override public CertificateType type() { return CertificateType.ORGANIZER; }
        @Override public String getTitle() { return "Certificate of Appreciation"; }
        @Override public String getCertificateText() {
            return "for serving as Organizer of " + eventTitle() + ", " + organizationName();
        }
        @Override public String getRoleLine() { return "Role: Event Organizer"; }
        @Override public String getShortType() { return "Appreciation"; }
    }
}
