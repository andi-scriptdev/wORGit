package edu.bsu.worgit.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.EventRows;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.repository.StatsRepository;

/**
 * AD-01. System Administrator (p25): campus totals, members per organization, needs attention.
 * Facilitator (p38) and Adviser (read-only): an organization selector; the four tiles are for the selected
 * organization (first by name unless orgId is given); members per organization covers all of theirs (decision 13).
 * "This week" is Monday to Sunday, Asia/Manila (decision 12).
 */
@Service
public class DashboardService {

    private final StatsRepository stats;
    private final Clock clock;

    public DashboardService(StatsRepository stats, Clock clock) {
        this.stats = stats;
        this.clock = clock;
    }

    public EventViews.AdminDashboard dashboard(AuthenticatedUser actor, UUID orgId) {
        Permissions.requireGlobal(actor, Capability.VIEW_DASHBOARD);
        LocalDate today = LocalDate.now(clock);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        if (actor.role() == Role.SYSTEM_ADMINISTRATOR) {
            return new EventViews.AdminDashboard("ALL", stats.campusTotals(),
                    new EventViews.NeedsAttention(stats.pendingApplications(null), stats.unpaidFees(null),
                            stats.eventsBetween(null, monday, sunday)),
                    null, null, null, stats.membersPerOrganization(null), false);
        }

        Set<UUID> managed = actor.managedOrgIds();
        List<EventRows.OrgCount> counts = stats.membersPerOrganization(managed);
        List<EventRows.OrgCount> byName = counts.stream()
                .sorted(Comparator.comparing((EventRows.OrgCount c) -> c.name().toLowerCase(Locale.ROOT)))
                .toList();
        if (orgId != null && !managed.contains(orgId)) {
            throw new ForbiddenException();
        }
        EventRows.OrgCount selected = orgId != null
                ? byName.stream().filter(c -> c.orgId().equals(orgId)).findFirst().orElse(null)
                : byName.stream().findFirst().orElse(null);
        EventViews.OrgTiles tiles = null;
        if (selected != null) {
            Set<UUID> one = Set.of(selected.orgId());
            tiles = new EventViews.OrgTiles(selected.members(), stats.pendingApplications(one), stats.unpaidFees(one),
                    stats.eventsBetween(one, monday, sunday));
        }
        boolean readOnly = actor.role() == Role.FACULTY_ADVISER;
        return new EventViews.AdminDashboard("MANAGED", null, null,
                byName.stream().map(c -> new PeopleViews.OrgRef(c.orgId(), c.name())).toList(),
                selected == null ? null : selected.orgId(), tiles, counts, readOnly);
    }
}
