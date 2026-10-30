package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BasisPoints;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

// Insert only: the writer role has no UPDATE on this table, so the record stays as it was registered.
public final class JdbcAdditionalDiscountRepository implements AdditionalDiscountRepository {
    private final JdbcTemplate jdbc;

    public JdbcAdditionalDiscountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AdditionalDiscount discount) {
        try {
            jdbc.update("INSERT INTO commercial_reservation.reservation_additional_discount "
                    + "(additional_discount_id, tenant_id, reservation_id, discount_percentage_bp, reason, applied_by, "
                    + "applied_at, previous_value_cents, amount_cents) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), discount.tenantId(), discount.reservationId(), discount.rate().value(),
                    discount.reason(), discount.authorizerId(),
                    discount.registeredAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC),
                    discount.line().base().amount(), discount.line().amount().amount());
        } catch (DuplicateKeyException duplicate) {
            // The unique key is the last guard when two registrations race past the existence check.
            throw new BusinessRuleViolation("the reservation already has an additional discount");
        }
    }

    // The order is not stored: it is the place after the fixed lines that took something, as the snapshot counts it.
    @Override
    public Optional<AdditionalDiscount> findByReservation(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT a.discount_percentage_bp, a.reason, a.applied_by, a.applied_at, a.previous_value_cents, "
                + "a.amount_cents, (SELECT count(*) FROM commercial_reservation.reservation_fixed_discount f "
                + "WHERE f.reservation_id = a.reservation_id AND f.amount_cents > 0) + 1 AS line_order "
                + "FROM commercial_reservation.reservation_additional_discount a WHERE a.tenant_id = ? AND a.reservation_id = ?",
                (row, index) -> {
                    long previous = row.getLong("previous_value_cents"), amount = row.getLong("amount_cents");
                    return new AdditionalDiscount(tenantId, reservationId, new BasisPoints(row.getInt("discount_percentage_bp")),
                            new DiscountLine(new Cents(previous), new Cents(amount), new Cents(previous - amount)),
                            row.getString("reason"), row.getObject("applied_by", UUID.class), row.getInt("line_order"),
                            row.getObject("applied_at", OffsetDateTime.class).toInstant());
                }, tenantId, reservationId).stream().findFirst();
    }

    @Override
    public boolean existsByReservation(UUID tenantId, UUID reservationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM "
                + "commercial_reservation.reservation_additional_discount WHERE tenant_id = ? AND reservation_id = ?)",
                Boolean.class, tenantId, reservationId));
    }
}
