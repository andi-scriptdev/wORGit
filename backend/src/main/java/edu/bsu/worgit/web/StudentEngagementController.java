package edu.bsu.worgit.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.service.CertificateService;
import edu.bsu.worgit.service.EventViews;
import edu.bsu.worgit.service.ReportService;

/** ST-01 Dashboard, ST-06 My Engagement Report, ST-07 Certificate. Students only (SecurityConfig). */
@RestController
@RequestMapping("/api/student")
public class StudentEngagementController {

    private final ReportService reports;
    private final CertificateService certificates;

    public StudentEngagementController(ReportService reports, CertificateService certificates) {
        this.reports = reports;
        this.certificates = certificates;
    }

    @GetMapping("/dashboard")
    public EventViews.StudentDashboard dashboard(@AuthenticationPrincipal AuthenticatedUser me) {
        return reports.dashboard(me);
    }

    @GetMapping("/report")
    public EventViews.EngagementReport report(@AuthenticationPrincipal AuthenticatedUser me) {
        return reports.report(me);
    }

    @GetMapping("/certificates/{certificateId}")
    public EventViews.CertificateView certificate(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @PathVariable String certificateId) {
        return certificates.get(me, certificateId);
    }
}
