package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import edu.bsu.worgit.domain.EventRows;

/** Counts for dashboards (ST-01, AD-01), the engagement report (ST-06) and the student summary (AD-05). */
public interface StatsRepository {

    /** Event counts only include events dated on or before upTo. */
    EventRows.StudentStats studentStats(String studentId, LocalDate upTo);

    EventRows.CampusTotals campusTotals();

    /** Largest first. scope null = every organization, empty = none. */
    List<EventRows.OrgCount> membersPerOrganization(Set<UUID> scope);

    int pendingApplications(Set<UUID> scope);

    /** Unpaid payment records. */
    int unpaidFees(Set<UUID> scope);

    int eventsBetween(Set<UUID> scope, LocalDate from, LocalDate to);

    /**
     * Students whose full name, display name or id contains the text (case-insensitive), by name.
     * scope null = every student; otherwise only members of those organizations.
     */
    List<EventRows.StudentRef> searchStudents(String text, Set<UUID> scope, int limit);

    boolean isMemberOfAny(String studentId, Set<UUID> orgIds);
}
