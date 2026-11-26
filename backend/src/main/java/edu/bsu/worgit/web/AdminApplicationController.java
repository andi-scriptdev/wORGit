package edu.bsu.worgit.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import edu.bsu.worgit.domain.ApplicationStatus;
import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.service.ApplicationService;
import edu.bsu.worgit.service.Views;

/** AD-06. */
@RestController
@RequestMapping("/api/admin/applications")
public class AdminApplicationController {

    private final ApplicationService applications;

    public AdminApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    public Views.ApplicationList list(@AuthenticationPrincipal AuthenticatedUser me,
                                      @RequestParam(required = false) ApplicationStatus status,
                                      @RequestParam(required = false) UUID orgId) {
        return applications.list(me, status, orgId);
    }

    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        applications.approve(me, id);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        applications.reject(me, id);
    }
}
