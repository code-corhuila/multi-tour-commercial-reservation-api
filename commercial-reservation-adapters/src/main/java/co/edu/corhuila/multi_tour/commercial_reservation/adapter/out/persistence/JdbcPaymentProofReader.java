package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSource;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSourcePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The read side of the review: the attempts of one tenant by status, oldest first, optionally presented at or before a cutoff (the
// overdue ones). The page is one query on the attempts; each one is joined to its reservation through the repository. No file is read.
public final class JdbcPaymentProofReader implements PaymentProofReader {
    private final JdbcTemplate jdbc;
    private final ReservationRepository reservations;

    public JdbcPaymentProofReader(JdbcTemplate jdbc, ReservationRepository reservations) {
        this.jdbc = jdbc;
        this.reservations = reservations;
    }

    @Override
    public ProofSourcePage find(UUID tenantId, ValidationStatus status, Instant registeredAtOrBefore, int page, int limit) {
        List<UUID[]> ids = jdbc.query("SELECT payment_attempt_id, reservation_id FROM commercial_reservation.payment_attempt "
                + "WHERE tenant_id = ? AND validation_status = ? AND (?::timestamptz IS NULL OR registered_at <= ?) "
                + "ORDER BY registered_at, payment_attempt_id LIMIT ? OFFSET ?", (row, index) -> new UUID[] {
                        row.getObject("payment_attempt_id", UUID.class), row.getObject("reservation_id", UUID.class)},
                tenantId, status.name(), cutoff(registeredAtOrBefore), cutoff(registeredAtOrBefore), limit, (page - 1L) * limit);
        List<ProofSource> items = new ArrayList<>();
        for (UUID[] id : ids) {
            reservations.findByTenantAndId(tenantId, id[1]).ifPresent(reservation -> reservation.attempts().stream()
                    .filter(attempt -> attempt.attemptId().equals(id[0])).findFirst()
                    .ifPresent(attempt -> items.add(new ProofSource(reservation, attempt))));
        }
        return new ProofSourcePage(items, countAtOrBefore(tenantId, status, registeredAtOrBefore));
    }

    @Override
    public long countAtOrBefore(UUID tenantId, ValidationStatus status, Instant registeredAtOrBefore) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.payment_attempt WHERE tenant_id = ? AND validation_status = ? "
                + "AND (?::timestamptz IS NULL OR registered_at <= ?)", Long.class, tenantId, status.name(), cutoff(registeredAtOrBefore),
                cutoff(registeredAtOrBefore));
    }

    private static OffsetDateTime cutoff(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
