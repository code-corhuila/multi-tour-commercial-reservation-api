package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-016 plan D-02 of the db (-db 030, guard 006): one row per financial decision, never deleted (no DELETE was granted). A save updates the columns that an
// open decision may change (amounts, freeze, outcome group) and inserts when the row does not exist yet; the guard of the engine refuses any other change.
// Every query leads with the tenant. The justification is stored and read, never logged.
public final class JdbcFinancialDecisionRepository implements FinancialDecisionRepository {
    static final String COLUMNS = "financial_decision_id, tenant_id, reservation_id, credit_balance_id, decision_kind, base_amount_cents, "
            + "penalty_amount_cents, recognized_at, frozen_at, remaining_at_freeze_minutes, outcome, decided_by_system, decided_by, decided_at, "
            + "rejection_justification, resumed_due_at";
    private final JdbcTemplate jdbc;

    public JdbcFinancialDecisionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FinancialDecision> find(UUID tenantId, UUID financialDecisionId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM commercial_reservation.financial_decision WHERE tenant_id = ? AND financial_decision_id = ?",
                (row, index) -> map(row), tenantId, financialDecisionId).stream().findFirst();
    }

    @Override
    public Optional<FinancialDecision> findLatest(UUID tenantId, UUID reservationId, DecisionKind kind) {
        return jdbc.query("SELECT " + COLUMNS + " FROM commercial_reservation.financial_decision WHERE tenant_id = ? AND reservation_id = ? "
                + "AND decision_kind = ? ORDER BY recognized_at DESC, financial_decision_id DESC LIMIT 1",
                (row, index) -> map(row), tenantId, reservationId, kind.name()).stream().findFirst();
    }

    @Override
    public void save(FinancialDecision d) {
        Boolean bySystem = d.outcome() == null ? null : d.decidedBySystem();
        int updated = jdbc.update("UPDATE commercial_reservation.financial_decision SET base_amount_cents = ?, penalty_amount_cents = ?, frozen_at = ?, "
                + "remaining_at_freeze_minutes = ?, outcome = ?, decided_by_system = ?, decided_by = ?, decided_at = ?, rejection_justification = ?, resumed_due_at = ? "
                + "WHERE tenant_id = ? AND financial_decision_id = ?", d.baseAmount().amount(), d.penalty() == null ? null : d.penalty().amount(),
                time(d.frozenAt()), d.remainingAtFreezeMinutes() == null ? null : d.remainingAtFreezeMinutes().intValue(),
                d.outcome() == null ? null : d.outcome().name(), bySystem, d.decidedBy(), time(d.decidedAt()), d.rejectionJustification(), time(d.resumedDueAt()), d.tenantId(), d.id());
        if (updated == 0) {
            jdbc.update("INSERT INTO commercial_reservation.financial_decision (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    d.id(), d.tenantId(), d.reservationId(), d.creditBalanceId(), d.kind().name(), d.baseAmount().amount(),
                    d.penalty() == null ? null : d.penalty().amount(), time(d.recognizedAt()), time(d.frozenAt()),
                    d.remainingAtFreezeMinutes() == null ? null : d.remainingAtFreezeMinutes().intValue(), d.outcome() == null ? null : d.outcome().name(),
                    bySystem, d.decidedBy(), time(d.decidedAt()), d.rejectionJustification(), time(d.resumedDueAt()));
        }
    }

    static FinancialDecision map(ResultSet row) throws SQLException {
        long penalty = row.getLong("penalty_amount_cents");
        Cents penaltyCents = row.wasNull() ? null : new Cents(penalty);
        long minutes = row.getLong("remaining_at_freeze_minutes");
        Long remaining = row.wasNull() ? null : minutes;
        String outcome = row.getString("outcome");
        boolean bySystem = row.getBoolean("decided_by_system");
        return new FinancialDecision(row.getObject("financial_decision_id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getObject("reservation_id", UUID.class), row.getObject("credit_balance_id", UUID.class), DecisionKind.valueOf(row.getString("decision_kind")),
                new Cents(row.getLong("base_amount_cents")), penaltyCents, instant(row, "recognized_at"), instant(row, "frozen_at"), remaining,
                outcome == null ? null : DecisionOutcome.valueOf(outcome), bySystem, row.getObject("decided_by", UUID.class), instant(row, "decided_at"),
                row.getString("rejection_justification"), instant(row, "resumed_due_at"));
    }

    static OffsetDateTime time(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
