package edu.bsu.worgit.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.service.PaymentService;

/** AD-07. */
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final PaymentService payments;

    public AdminPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    public List<Rows.Payment> list(@AuthenticationPrincipal AuthenticatedUser me,
                                   @RequestParam(required = false) UUID orgId) {
        return payments.list(me, orgId);
    }

    @PostMapping("/record")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody PaymentService.RecordInput body) {
        payments.record(me, body);
    }

    @PostMapping("/{paymentId}/mark-paid")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markPaid(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID paymentId) {
        payments.markPaid(me, paymentId);
    }
}
