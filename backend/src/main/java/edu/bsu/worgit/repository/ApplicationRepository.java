package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import edu.bsu.worgit.domain.ApplicationStatus;
import edu.bsu.worgit.domain.Rows;

public interface ApplicationRepository {

    boolean hasPendingApplication(String studentId, UUID orgId);

    /** Inserts a PENDING application. */
    UUID insertApplication(String studentId, UUID orgId, LocalDate dateApplied);

    Optional<Rows.Application> findApplication(UUID applicationId);

    /** Changes PENDING to the new status. False when the application was not pending any more. */
    boolean decideApplication(UUID applicationId, ApplicationStatus newStatus);

    /** Newest first. status null = all; onlyOrgIds null = all organizations; orgFilter null = no filter. */
    List<Rows.Application> listApplications(ApplicationStatus status, Set<UUID> onlyOrgIds, UUID orgFilter);

    Map<ApplicationStatus, Integer> countApplications(Set<UUID> onlyOrgIds, UUID orgFilter);

    /** One student's applications, newest first. */
    List<Rows.Application> listApplicationsOf(String studentId);
}
