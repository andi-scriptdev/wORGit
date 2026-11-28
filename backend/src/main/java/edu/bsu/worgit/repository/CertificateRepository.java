package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import edu.bsu.worgit.domain.CertificateType;
import edu.bsu.worgit.domain.EventRows;

public interface CertificateRepository {

    Optional<EventRows.CertificateRow> findCertificate(String certificateId);

    /** Returns the new id (CERT-0001...). DuplicateKeyException when the student already has one for the event. */
    String insertCertificate(String studentId, UUID eventId, String eventTitle, CertificateType type, LocalDate issued);

    /** Newest first. */
    List<EventRows.CertificateRow> certificatesOf(String studentId);

    int certificateCount(UUID eventId);
}
