package edu.bsu.worgit.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.Certificate;
import edu.bsu.worgit.domain.CertificateType;
import edu.bsu.worgit.domain.EventRows;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.exception.CertificateNotEligibleException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.CertificateRepository;
import edu.bsu.worgit.repository.EventAdminRepository;

/**
 * CertificateGenerator of the class diagram (AD-11 Certificates tab) and ST-07. A certificate is only made for
 * a participant who attended; the role at that moment decides Participation or Appreciation (decisions 7, 9).
 * One certificate per student per event: generating again returns the existing one.
 */
@Service
public class CertificateService {

    private final CertificateRepository certificates;
    private final EventAdminRepository events;
    private final EventService eventService;
    private final Clock clock;

    public CertificateService(CertificateRepository certificates, EventAdminRepository events, EventService eventService,
                              Clock clock) {
        this.certificates = certificates;
        this.events = events;
        this.eventService = eventService;
        this.clock = clock;
    }

    /** "Generate" on one row. 422 CERTIFICATE_NOT_ELIGIBLE: "{name} has no recorded attendance, so no certificate was created." */
    @Transactional
    public EventViews.CertificateView generateFor(AuthenticatedUser actor, UUID eventId, String studentId) {
        EventRows.EventInfo event = eventService.requireManaged(actor, eventId);
        EventRows.Participant p = eventService.participantOrNull(eventId, studentId);
        if (p == null) {
            throw new NotFoundException();
        }
        if (p.certificateId() != null) {
            return view(certificates.findCertificate(p.certificateId()).orElseThrow(NotFoundException::new));
        }
        if (!p.attended()) {
            throw new CertificateNotEligibleException(p.fullName());
        }
        return view(issue(event, p));
    }

    /** "Generate for all attendees": certificates for every attendee without one; absent ones are listed by name. */
    @Transactional
    public EventViews.GenerationResult generateForAll(AuthenticatedUser actor, UUID eventId) {
        EventRows.EventInfo event = eventService.requireManaged(actor, eventId);
        List<EventViews.CertificateView> created = new ArrayList<>();
        List<String> notEligible = new ArrayList<>();
        int already = 0;
        for (EventRows.Participant p : events.participants(eventId)) {
            if (p.certificateId() != null) {
                already++;
            } else if (!p.attended()) {
                notEligible.add(p.fullName());
            } else {
                created.add(view(issue(event, p)));
            }
        }
        return new EventViews.GenerationResult(created, already, notEligible);
    }

    /** ST-07 for the student who owns it; "View" for the staff who manage the event's organization. */
    public EventViews.CertificateView get(AuthenticatedUser user, String certificateId) {
        EventRows.CertificateRow row = certificates.findCertificate(certificateId).orElseThrow(NotFoundException::new);
        if (user.role() == Role.STUDENT) {
            if (!row.studentId().equals(user.userId())) {
                throw new NotFoundException();
            }
        } else {
            Permissions.requireForOrg(user, Capability.MANAGE_EVENTS, row.orgId());
        }
        return view(row);
    }

    private EventRows.CertificateRow issue(EventRows.EventInfo event, EventRows.Participant p) {
        String id;
        try {
            id = certificates.insertCertificate(p.studentId(), event.eventId(), event.title(),
                    CertificateType.forRole(p.role()), LocalDate.now(clock));
        } catch (DuplicateKeyException e) {
            // made in the meantime (double click): return that one
            id = eventService.participantOrNull(event.eventId(), p.studentId()).certificateId();
        }
        return certificates.findCertificate(id).orElseThrow(NotFoundException::new);
    }

    static EventViews.CertificateView view(EventRows.CertificateRow row) {
        Certificate c = row.toCertificate();
        return new EventViews.CertificateView(c.certificateId(), c.getTitle(), c.studentName(), c.getCertificateText(),
                c.getRoleLine(), c.eventTitle(), c.organizationName(), c.dateIssued(), c.getShortType());
    }
}
