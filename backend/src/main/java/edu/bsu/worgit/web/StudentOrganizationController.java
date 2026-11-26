package edu.bsu.worgit.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.service.MembershipService;
import edu.bsu.worgit.service.OrganizationService;
import edu.bsu.worgit.service.Views;

/** ST-02 and ST-03. Everything here needs a student (see SecurityConfig). */
@RestController
@RequestMapping("/api/student")
public class StudentOrganizationController {

    private final OrganizationService organizations;
    private final MembershipService memberships;

    public StudentOrganizationController(OrganizationService organizations, MembershipService memberships) {
        this.organizations = organizations;
        this.memberships = memberships;
    }

    @GetMapping("/organizations")
    public List<Views.OrgCard> browse(@AuthenticationPrincipal AuthenticatedUser me,
                                      @RequestParam(required = false) String search,
                                      @RequestParam(required = false) String category) {
        return organizations.browse(me, search, category);
    }

    @GetMapping("/organizations/mine")
    public List<Views.OrgCard> mine(@AuthenticationPrincipal AuthenticatedUser me) {
        return organizations.mine(me);
    }

    @GetMapping("/applications")
    public List<Views.MyApplication> myApplications(@AuthenticationPrincipal AuthenticatedUser me) {
        return organizations.myApplications(me);
    }

    @GetMapping("/organizations/{orgId}")
    public Views.OrgDetail detail(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID orgId) {
        return organizations.detail(me, orgId);
    }

    @PostMapping("/organizations/{orgId}/join")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void join(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID orgId) {
        memberships.join(me, orgId);
    }

    @PostMapping("/organizations/{orgId}/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apply(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID orgId) {
        memberships.apply(me, orgId);
    }

    @DeleteMapping("/organizations/{orgId}/membership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID orgId) {
        memberships.leave(me, orgId);
    }
}
