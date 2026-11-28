package edu.bsu.worgit.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.EventRows;
import edu.bsu.worgit.service.DashboardService;
import edu.bsu.worgit.service.EventViews;
import edu.bsu.worgit.service.StudentSummaryService;

/** AD-01 Dashboard and AD-05 Student Summary. */
@RestController
@RequestMapping("/api/admin")
public class AdminInsightsController {

    private final DashboardService dashboards;
    private final StudentSummaryService summaries;

    public AdminInsightsController(DashboardService dashboards, StudentSummaryService summaries) {
        this.dashboards = dashboards;
        this.summaries = summaries;
    }

    @GetMapping("/dashboard")
    public EventViews.AdminDashboard dashboard(@AuthenticationPrincipal AuthenticatedUser me,
                                               @RequestParam(required = false) UUID orgId) {
        return dashboards.dashboard(me, orgId);
    }

    @GetMapping("/students")
    public List<EventRows.StudentRef> search(@AuthenticationPrincipal AuthenticatedUser me,
                                             @RequestParam(required = false) String search) {
        return summaries.search(me, search);
    }

    @GetMapping("/students/{studentId}/summary")
    public EventViews.StudentSummary summary(@AuthenticationPrincipal AuthenticatedUser me,
                                             @PathVariable String studentId) {
        return summaries.summary(me, studentId);
    }
}
