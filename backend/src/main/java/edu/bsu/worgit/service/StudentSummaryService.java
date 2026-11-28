package edu.bsu.worgit.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.EventRows;
import edu.bsu.worgit.domain.PeopleRows;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.ProfileRepository;
import edu.bsu.worgit.repository.StatsRepository;

/**
 * AD-05 Student Summary (read-only). The search page: System Administrator (every student) and Adviser (students
 * of their organizations). The summary itself: also Facilitators, for students of their organizations, through
 * the "Summary" buttons (decision 16).
 */
@Service
public class StudentSummaryService {

    static final int SEARCH_LIMIT = 20;

    private final StatsRepository stats;
    private final ProfileRepository profiles;
    private final Clock clock;

    public StudentSummaryService(StatsRepository stats, ProfileRepository profiles, Clock clock) {
        this.stats = stats;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** Blank search = no results (the page starts empty). */
    public List<EventRows.StudentRef> search(AuthenticatedUser actor, String text) {
        Permissions.requireGlobal(actor, Capability.OPEN_STUDENT_SUMMARY_PAGE);
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return stats.searchStudents(text.strip(), Permissions.visibleOrgIds(actor), SEARCH_LIMIT);
    }

    public EventViews.StudentSummary summary(AuthenticatedUser actor, String studentId) {
        Permissions.requireGlobal(actor, Capability.VIEW_STUDENT_SUMMARY);
        PeopleRows.StudentProfile p = profiles.findStudentProfile(studentId).orElseThrow(NotFoundException::new);
        Set<UUID> scope = Permissions.visibleOrgIds(actor);
        if (scope != null && !stats.isMemberOfAny(studentId, scope)) {
            throw new ForbiddenException();
        }
        return new EventViews.StudentSummary(p.userId(), p.fullName(), p.displayName(),
                ReportService.statsOf(stats.studentStats(studentId, LocalDate.now(clock))), p.interests());
    }
}
