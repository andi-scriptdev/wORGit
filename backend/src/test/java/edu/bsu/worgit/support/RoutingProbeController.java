package edu.bsu.worgit.support;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.bsu.worgit.domain.AuthenticatedUser;

/** Test-only endpoints, one per route area, to prove the role routing. Not shipped. */
@RestController
public class RoutingProbeController {

    @GetMapping("/api/student/probe")
    public String student(@AuthenticationPrincipal AuthenticatedUser me) {
        return me.userId();
    }

    @GetMapping("/api/admin/probe")
    public String admin(@AuthenticationPrincipal AuthenticatedUser me) {
        return me.role().name();
    }

    @GetMapping("/api/shared/probe")
    public String shared(@AuthenticationPrincipal AuthenticatedUser me) {
        return me.userId();
    }
}
