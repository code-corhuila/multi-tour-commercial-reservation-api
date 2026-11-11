package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameterValues;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// payment_condition: one row per payment type and, optionally, per service; a change replaces the row, keeping only the last author,
// reason and time (DP-04, DD-5). Nothing is deleted. The tenant-level row and each service row are separate unique keys.
public final class JdbcPaymentParameterRepository implements PaymentParameterRepository {
    private final JdbcTemplate jdbc;

    public JdbcPaymentParameterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PaymentParameter> findByTenant(UUID tenantId) {
        return jdbc.query("SELECT * FROM commercial_reservation.payment_condition WHERE tenant_id = ? "
                + "ORDER BY payment_type, service_id NULLS FIRST", (row, index) -> {
                    int minimum = row.getInt("minimum_deposit_bp");
                    Integer minimumBp = row.wasNull() ? null : minimum;
                    int advance = row.getInt("balance_deadline_advance_hours");
                    Integer advanceHours = row.wasNull() ? null : advance;
                    var values = new PaymentParameterValues(PaymentType.fromCode(row.getString("payment_type")),
                            row.getObject("service_id", UUID.class), row.getInt("initial_wait_hours"), row.getInt("additional_wait_hours"),
                            minimumBp, advanceHours, row.getBoolean("transfer_enabled"), row.getBoolean("cash_enabled"));
                    return new PaymentParameter(values, row.getObject("changed_by", UUID.class), row.getString("change_reason"),
                            row.getObject("changed_at", OffsetDateTime.class).toInstant());
                }, tenantId);
    }

    @Override
    public void save(UUID tenantId, PaymentParameter change) {
        var v = change.values();
        OffsetDateTime at = change.changedAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        int updated = jdbc.update("UPDATE commercial_reservation.payment_condition SET initial_wait_hours = ?, additional_wait_hours = ?, "
                + "minimum_deposit_bp = ?, balance_deadline_advance_hours = ?, transfer_enabled = ?, cash_enabled = ?, changed_by = ?, "
                + "change_reason = ?, changed_at = ? WHERE tenant_id = ? AND payment_type = ? AND service_id IS NOT DISTINCT FROM ?::uuid",
                v.initialWaitHours(), v.additionalWaitHours(), v.minimumDepositBp(), v.balanceDeadlineAdvanceHours(), v.transferEnabled(),
                v.cashEnabled(), change.changedBy(), change.changeReason(), at, tenantId, v.paymentType().name(), v.serviceId());
        if (updated > 0) return;
        jdbc.update("INSERT INTO commercial_reservation.payment_condition (payment_condition_id, tenant_id, service_id, payment_type, "
                + "initial_wait_hours, additional_wait_hours, minimum_deposit_bp, balance_deadline_advance_hours, transfer_enabled, "
                + "cash_enabled, changed_by, change_reason, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, v.serviceId(), v.paymentType().name(), v.initialWaitHours(), v.additionalWaitHours(),
                v.minimumDepositBp(), v.balanceDeadlineAdvanceHours(), v.transferEnabled(), v.cashEnabled(), change.changedBy(),
                change.changeReason(), at);
    }
}
