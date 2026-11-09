package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.instant;
import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.time;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceApplications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditOrigin;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-017 plan D-02, D-03 of the db (-db 032, 033, guard 009): a credit balance row is a fact and is never updated (UPDATE is withdrawn), so what was applied and
// what is set aside are derived on every read from the applications and from the live refund requests (an open or authorized decision of that kind), the same sums
// the guard of the engine uses. Saving a credit that exists changes nothing; saving an application appends one row that the guard accepts or refuses. Every query
// leads with the tenant, so a credit of another tenant looks missing.
public final class JdbcCreditBalanceRepository implements CreditBalanceRepository, CreditBalanceApplications {
    private static final String SELECT = "SELECT c.credit_balance_id, c.tenant_id, c.source_reservation_id, c.credit_origin, c.amount_cents, "
            + "c.source_financial_decision_id, c.source_payment_attempt_id, c.recorded_at, c.recorded_by, "
            + "COALESCE((SELECT sum(a.applied_amount_cents) FROM commercial_reservation.credit_balance_application a "
            + "WHERE a.credit_balance_id = c.credit_balance_id), 0) AS applied_cents, "
            + "COALESCE((SELECT sum(d.base_amount_cents) FROM commercial_reservation.financial_decision d WHERE d.credit_balance_id = c.credit_balance_id "
            + "AND d.decision_kind = 'CREDIT_BALANCE_REFUND_REQUEST' AND (d.outcome IS NULL OR d.outcome = 'AUTHORIZED')), 0) AS set_aside_cents "
            + "FROM commercial_reservation.credit_balance c WHERE c.tenant_id = ? AND ";
    private final JdbcTemplate jdbc;

    public JdbcCreditBalanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CreditBalance> find(UUID tenantId, UUID creditBalanceId) {
        return one("c.credit_balance_id = ?", tenantId, creditBalanceId);
    }

    @Override
    public Optional<CreditBalance> findByDecision(UUID tenantId, UUID financialDecisionId) {
        return one("c.source_financial_decision_id = ?", tenantId, financialDecisionId);
    }

    @Override
    public Optional<CreditBalance> findByAttempt(UUID tenantId, UUID paymentAttemptId) {
        return one("c.source_payment_attempt_id = ?", tenantId, paymentAttemptId);
    }

    @Override
    public void save(CreditBalance c) {
        jdbc.update("INSERT INTO commercial_reservation.credit_balance (credit_balance_id, tenant_id, source_reservation_id, credit_origin, amount_cents, "
                + "source_financial_decision_id, source_payment_attempt_id, recorded_at, recorded_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                c.id(), c.tenantId(), c.sourceReservationId(), c.origin().name(), c.amount().amount(), c.sourceDecisionId(), c.sourceAttemptId(),
                time(c.recordedAt()), c.recordedBy());
    }

    @Override
    public void save(CreditApplication a) {
        jdbc.update("INSERT INTO commercial_reservation.credit_balance_application (credit_balance_application_id, credit_balance_id, tenant_id, "
                + "target_reservation_id, applied_amount_cents, applied_at, applied_by, applied_by_kind) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                a.id(), a.creditBalanceId(), a.tenantId(), a.targetReservationId(), a.amount().amount(), time(a.appliedAt()), a.appliedBy(), a.kind().name());
    }

    private Optional<CreditBalance> one(String condition, UUID tenantId, UUID key) {
        return jdbc.query(SELECT + condition, (row, index) -> map(row), tenantId, key).stream().findFirst();
    }

    private static CreditBalance map(ResultSet row) throws SQLException {
        return new CreditBalance(row.getObject("credit_balance_id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getObject("source_reservation_id", UUID.class), CreditOrigin.valueOf(row.getString("credit_origin")), new Cents(row.getLong("amount_cents")),
                row.getObject("source_financial_decision_id", UUID.class), row.getObject("source_payment_attempt_id", UUID.class),
                instant(row, "recorded_at"), row.getObject("recorded_by", UUID.class), new Cents(row.getLong("applied_cents")),
                new Cents(row.getLong("set_aside_cents")));
    }
}
