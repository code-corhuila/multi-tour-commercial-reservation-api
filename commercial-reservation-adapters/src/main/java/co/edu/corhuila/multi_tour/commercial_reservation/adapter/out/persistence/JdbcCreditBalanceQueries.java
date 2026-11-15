package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.instant;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditSummary;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication.AppliedBy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-017 AC-17.9, RNF-01: the reads of the credit balances over the tables of the db. Every query leads with the tenant, the order is stable, and the applied
// and set-aside values are the same sums the repository and the guard use, so the available value is derived on every read and never stored.
public final class JdbcCreditBalanceQueries implements CreditBalanceQueries {
    private static final String VIEW = "SELECT t.*, r.customer_id FROM (" + JdbcCreditBalanceRepository.PROJECTION + "WHERE c.tenant_id = ?) t "
            + "JOIN commercial_reservation.reservation r ON r.reservation_id = t.source_reservation_id WHERE TRUE";
    private static final String AVAILABLE = " AND t.amount_cents - t.applied_cents - t.set_aside_cents > 0";
    private final JdbcTemplate jdbc;

    public JdbcCreditBalanceQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RefundPage<CreditBalanceView> page(UUID tenantId, UUID customerId, boolean availableOnly, int page, int limit) {
        List<Object> args = new ArrayList<>(List.of(tenantId));
        StringBuilder where = new StringBuilder();
        if (customerId != null) { where.append(" AND r.customer_id = ?"); args.add(customerId); }
        if (availableOnly) where.append(AVAILABLE);
        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + VIEW + where + ") counted", Long.class, args.toArray());
        args.add(limit);
        args.add((long) (page - 1) * limit);
        List<CreditBalanceView> items = jdbc.query(VIEW + where + " ORDER BY t.recorded_at DESC, t.credit_balance_id DESC LIMIT ? OFFSET ?",
                (row, index) -> new CreditBalanceView(JdbcCreditBalanceRepository.map(row), row.getObject("customer_id", UUID.class)), args.toArray());
        return new RefundPage<>(items, total == null ? 0 : total);
    }

    @Override
    public Optional<CreditBalanceView> find(UUID tenantId, UUID customerId, UUID creditBalanceId) {
        List<Object> args = new ArrayList<>(List.of(tenantId, creditBalanceId));
        String own = "";
        if (customerId != null) { own = " AND r.customer_id = ?"; args.add(customerId); }
        return jdbc.query(VIEW + " AND t.credit_balance_id = ?" + own,
                (row, index) -> new CreditBalanceView(JdbcCreditBalanceRepository.map(row), row.getObject("customer_id", UUID.class)), args.toArray()).stream()
                .findFirst();
    }

    @Override
    public List<CreditApplication> applications(UUID tenantId, UUID creditBalanceId) {
        return jdbc.query("SELECT credit_balance_application_id, credit_balance_id, tenant_id, target_reservation_id, applied_amount_cents, applied_at, applied_by, "
                + "applied_by_kind FROM commercial_reservation.credit_balance_application WHERE tenant_id = ? AND credit_balance_id = ? "
                + "ORDER BY applied_at, credit_balance_application_id",
                (row, index) -> new CreditApplication(row.getObject("credit_balance_application_id", UUID.class), row.getObject("credit_balance_id", UUID.class),
                        row.getObject("tenant_id", UUID.class), row.getObject("target_reservation_id", UUID.class), new Cents(row.getLong("applied_amount_cents")),
                        instant(row, "applied_at"), row.getObject("applied_by", UUID.class), AppliedBy.valueOf(row.getString("applied_by_kind"))),
                tenantId, creditBalanceId);
    }

    @Override
    public List<FinancialDecision> refundRequests(UUID tenantId, UUID creditBalanceId) {
        return jdbc.query("SELECT " + JdbcFinancialDecisionRepository.COLUMNS + " FROM commercial_reservation.financial_decision WHERE tenant_id = ? "
                + "AND credit_balance_id = ? AND decision_kind = 'CREDIT_BALANCE_REFUND_REQUEST' ORDER BY recognized_at, financial_decision_id",
                (row, index) -> JdbcFinancialDecisionRepository.map(row), tenantId, creditBalanceId);
    }

    @Override
    public CreditSummary summary(UUID tenantId) {
        return jdbc.queryForObject("SELECT COALESCE(sum(t.amount_cents - t.applied_cents - t.set_aside_cents) FILTER (WHERE t.amount_cents - t.applied_cents "
                + "- t.set_aside_cents > 0), 0) AS available, count(DISTINCT r.customer_id) FILTER (WHERE t.amount_cents - t.applied_cents - t.set_aside_cents > 0) "
                + "AS customers, (SELECT count(*) FROM commercial_reservation.financial_decision d WHERE d.tenant_id = ? AND d.decision_kind = "
                + "'CREDIT_BALANCE_REFUND_REQUEST' AND d.outcome IS NULL) AS requests FROM (" + JdbcCreditBalanceRepository.PROJECTION + "WHERE c.tenant_id = ?) t "
                + "JOIN commercial_reservation.reservation r ON r.reservation_id = t.source_reservation_id",
                (row, index) -> new CreditSummary(row.getLong("available"), row.getLong("customers"), row.getLong("requests")), tenantId, tenantId);
    }
}
