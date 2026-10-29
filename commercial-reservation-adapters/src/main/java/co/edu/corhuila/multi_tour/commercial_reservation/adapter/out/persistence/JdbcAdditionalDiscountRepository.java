package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
            throw new DomainException("reservation", "the reservation already has an additional discount");
        }
    }

    @Override
    public boolean existsByReservation(UUID tenantId, UUID reservationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM "
                + "commercial_reservation.reservation_additional_discount WHERE tenant_id = ? AND reservation_id = ?)",
                Boolean.class, tenantId, reservationId));
    }
}
