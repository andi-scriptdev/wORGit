package edu.bsu.worgit.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.FeeStatus;
import edu.bsu.worgit.domain.PaymentStatus;
import edu.bsu.worgit.domain.Rows;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private static final String SELECT = """
            SELECT p.payment_id, p.student_id, u.full_name, p.org_id, o.name AS org_name,
                   p.amount, p.date_paid, p.status
              FROM payment_records p
              JOIN users u ON u.user_id = p.student_id
              JOIN organizations o ON o.org_id = p.org_id
            """;

    private final JdbcClient jdbc;

    public JdbcPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Rows.Payment map(ResultSet rs) throws SQLException {
        return new Rows.Payment(
                rs.getObject("payment_id", UUID.class),
                rs.getString("student_id"),
                rs.getString("full_name"),
                rs.getObject("org_id", UUID.class),
                rs.getString("org_name"),
                rs.getBigDecimal("amount"),
                rs.getObject("date_paid", LocalDate.class),
                PaymentStatus.valueOf(rs.getString("status")));
    }

    @Override
    public boolean hasPaymentRecord(String studentId, UUID orgId) {
        Boolean found = jdbc.sql("SELECT EXISTS (SELECT 1 FROM payment_records WHERE student_id = :student AND org_id = :org)")
                .param("student", studentId)
                .param("org", orgId)
                .query(Boolean.class).single();
        return Boolean.TRUE.equals(found);
    }

    @Override
    public void insertPayment(String studentId, UUID orgId, BigDecimal amount, LocalDate datePaid, PaymentStatus status) {
        jdbc.sql("""
                INSERT INTO payment_records (student_id, org_id, amount, date_paid, status)
                VALUES (:student, :org, :amount, :date, :status)
                """)
                .param("student", studentId)
                .param("org", orgId)
                .param("amount", amount)
                .param("date", datePaid)
                .param("status", status.name())
                .update();
    }

    @Override
    public Optional<UUID> findUnpaidPaymentId(String studentId, UUID orgId) {
        return jdbc.sql("""
                SELECT payment_id FROM payment_records
                 WHERE student_id = :student AND org_id = :org AND status = 'UNPAID'
                 ORDER BY payment_id LIMIT 1
                """)
                .param("student", studentId)
                .param("org", orgId)
                .query((rs, n) -> rs.getObject("payment_id", UUID.class))
                .optional();
    }

    @Override
    public Optional<Rows.Payment> findPayment(UUID paymentId) {
        return jdbc.sql(SELECT + " WHERE p.payment_id = :id")
                .param("id", paymentId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public boolean settlePayment(UUID paymentId, BigDecimal amount, LocalDate datePaid) {
        return jdbc.sql("""
                UPDATE payment_records SET status = 'PAID', amount = :amount, date_paid = :date
                 WHERE payment_id = :id AND status = 'UNPAID'
                """)
                .param("amount", amount)
                .param("date", datePaid)
                .param("id", paymentId)
                .update() > 0;
    }

    @Override
    public List<Rows.Payment> listPayments(Set<UUID> onlyOrgIds, UUID orgFilter) {
        if (onlyOrgIds != null && onlyOrgIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (onlyOrgIds != null) {
            sql.append(" AND p.org_id IN (:scope)");
            params.put("scope", onlyOrgIds);
        }
        if (orgFilter != null) {
            sql.append(" AND p.org_id = :orgFilter");
            params.put("orgFilter", orgFilter);
        }
        sql.append(" ORDER BY (p.status = 'UNPAID') DESC, u.full_name, p.payment_id");
        return jdbc.sql(sql.toString()).params(params).query((rs, n) -> map(rs)).list();
    }

    @Override
    public Map<String, FeeStatus> feeStatusByStudent(UUID orgId) {
        Map<String, FeeStatus> result = new HashMap<>();
        jdbc.sql("""
                SELECT student_id,
                       CASE WHEN bool_or(status = 'UNPAID') THEN 'UNPAID' ELSE 'PAID' END AS fee
                  FROM payment_records
                 WHERE org_id = :org
                 GROUP BY student_id
                """)
                .param("org", orgId)
                .query((rs, n) -> Map.entry(rs.getString("student_id"), FeeStatus.valueOf(rs.getString("fee"))))
                .list()
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    @Override
    public FeeStatus feeStatusOf(String studentId, UUID orgId) {
        return jdbc.sql("""
                SELECT CASE WHEN bool_or(status = 'UNPAID') THEN 'UNPAID' ELSE 'PAID' END AS fee
                  FROM payment_records
                 WHERE student_id = :student AND org_id = :org
                HAVING count(*) > 0
                """)
                .param("student", studentId)
                .param("org", orgId)
                .query((rs, n) -> FeeStatus.valueOf(rs.getString("fee")))
                .optional()
                .orElse(FeeStatus.NONE);
    }
}
