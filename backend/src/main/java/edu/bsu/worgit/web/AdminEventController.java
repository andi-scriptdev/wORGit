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
import edu.bsu.worgit.domain.EventRows;
import edu.bsu.worgit.service.CertificateService;
import edu.bsu.worgit.service.EventService;
import edu.bsu.worgit.service.EventViews;

/** AD-09 Events, AD-10 Create event, AD-11 Event detail and certificates. Role rules are in the services. */
@RestController
@RequestMapping("/api/admin")
public class AdminEventController {

    private final EventService events;
    private final CertificateService certificates;

    public AdminEventController(EventService events, CertificateService certificates) {
        this.events = events;
        this.certificates = certificates;
    }

    @GetMapping("/events")
    public List<EventRows.EventListRow> list(@AuthenticationPrincipal AuthenticatedUser me,
                                             @RequestParam(required = false) UUID orgId) {
        return events.list(me, orgId);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventViews.CreatedEvent create(@AuthenticationPrincipal AuthenticatedUser me,
                                          @RequestBody EventViews.EventInput body) {
        return new EventViews.CreatedEvent(events.create(me, body));
    }

    @GetMapping("/events/{eventId}")
    public EventViews.EventDetail detail(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID eventId) {
        return events.detail(me, eventId);
    }

    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID eventId) {
        events.delete(me, eventId);
    }

    @PostMapping("/events/{eventId}/participants")
    public EventViews.EventDetail addParticipant(@AuthenticationPrincipal AuthenticatedUser me,
                                                 @PathVariable UUID eventId,
                                                 @RequestBody EventViews.ParticipantInput body) {
        return events.addParticipant(me, eventId, body);
    }

    @DeleteMapping("/events/{eventId}/participants/{studentId}")
    public EventViews.EventDetail removeParticipant(@AuthenticationPrincipal AuthenticatedUser me,
                                                    @PathVariable UUID eventId, @PathVariable String studentId) {
        return events.removeParticipant(me, eventId, studentId);
    }

    @PutMapping("/events/{eventId}/attendance")
    public EventViews.EventDetail saveAttendance(@AuthenticationPrincipal AuthenticatedUser me,
                                                 @PathVariable UUID eventId,
                                                 @RequestBody EventViews.AttendanceInput body) {
        return events.saveAttendance(me, eventId, body);
    }

    @PostMapping("/events/{eventId}/certificates/{studentId}")
    public EventViews.CertificateView generateFor(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @PathVariable UUID eventId, @PathVariable String studentId) {
        return certificates.generateFor(me, eventId, studentId);
    }

    @PostMapping("/events/{eventId}/certificates")
    public EventViews.GenerationResult generateForAll(@AuthenticationPrincipal AuthenticatedUser me,
                                                      @PathVariable UUID eventId) {
        return certificates.generateForAll(me, eventId);
    }

    @GetMapping("/certificates/{certificateId}")
    public EventViews.CertificateView certificate(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @PathVariable String certificateId) {
        return certificates.get(me, certificateId);
    }
}
