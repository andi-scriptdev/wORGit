package edu.bsu.worgit.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import edu.bsu.worgit.domain.FeeStatus;
import edu.bsu.worgit.domain.PaymentStatus;
import edu.bsu.worgit.domain.Rows;

public interface PaymentRepository {

    boolean hasPaymentRecord(String studentId, UUID orgId);

    void insertPayment(String studentId, UUID orgId, BigDecimal amount, LocalDate datePaid, PaymentStatus status);

    Optional<UUID> findUnpaidPaymentId(String studentId, UUID orgId);

    Optional<Rows.Payment> findPayment(UUID paymentId);

    /** UNPAID -> PAID with the given amount and date. False when the record was not unpaid. */
    boolean settlePayment(UUID paymentId, BigDecimal amount, LocalDate datePaid);

    /** Unpaid first, then by student name. onlyOrgIds null = all; orgFilter null = no filter. */
    List<Rows.Payment> listPayments(Set<UUID> onlyOrgIds, UUID orgFilter);

    /** UNPAID if the student has any unpaid record for the organization, else PAID; students without records are absent. */
    Map<String, FeeStatus> feeStatusByStudent(UUID orgId);

    FeeStatus feeStatusOf(String studentId, UUID orgId);
}
