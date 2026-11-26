package edu.bsu.worgit.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.OrgInput;
import edu.bsu.worgit.domain.Organization;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.service.MembershipService;
import edu.bsu.worgit.service.OrganizationService;
import edu.bsu.worgit.service.Views;

/** AD-02, AD-03, AD-04. What each staff role may do is decided in the services (Permissions). */
@RestController
@RequestMapping("/api/admin")
public class AdminOrganizationController {

    private final OrganizationService organizations;
    private final MembershipService memberships;

    public AdminOrganizationController(OrganizationService organizations, MembershipService memberships) {
        this.organizations = organizations;
        this.memberships = memberships;
    }

    @GetMapping("/organizations")
    public List<Views.StaffOrgRow> list(@AuthenticationPrincipal AuthenticatedUser me,
                                        @RequestParam(required = false) String search) {
        return organizations.listForStaff(me, search);
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.CreatedOrg create(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody OrgInput body) {
        return new Views.CreatedOrg(organizations.create(me, body));
    }

    @GetMapping("/organizations/{orgId}")
    public Organization get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID orgId) {
        return organizations.getForStaff(me, orgId);
    }

    @PutMapping("/organizations/{orgId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID orgId,
                       @RequestBody OrgInput body) {
        organizations.update(me, orgId, body);
    }

    @GetMapping("/organizations/{orgId}/removal-impact")
    public Rows.RemovalImpact removalImpact(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID orgId) {
        return organizations.removalImpact(me, orgId);
    }

    @DeleteMapping("/organizations/{orgId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID orgId) {
        organizations.remove(me, orgId);
    }

    @GetMapping("/organizations/{orgId}/members")
    public List<Views.MemberView> members(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID orgId,
                                          @RequestParam(required = false) String search) {
        return memberships.members(me, orgId, search);
    }

    @DeleteMapping("/organizations/{orgId}/members/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID orgId,
                             @PathVariable String studentId) {
        memberships.removeMember(me, orgId, studentId);
    }
}
