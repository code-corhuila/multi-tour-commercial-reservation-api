package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OwnReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The reads of HU-COM-007 (AC-07.16, AC-07.17). The tenant is in every predicate, so another tenant's reservation is the same
// empty answer as a missing one (RN-09). Nothing is recalculated: the values are the stored ones (AC-07.10). The list joins the
// lead customer, the tour and the departure projection for the names and the start (portal screen 10); the detail is the
// reservation as created (JdbcReservationReader) plus the stored status, situation, balance and whether a seat is held.
// The payment attempts are not loaded: no screen of this story shows them and HU-COM-009 owns their adapter.
public final class JdbcReservationQueries implements ReservationQueries, OwnReservationQueries {
    private static final String FROM = " FROM commercial_reservation.reservation r "
            + "JOIN commercial_reservation.customer c ON c.tenant_id = r.tenant_id AND c.customer_id = r.customer_id "
            + "JOIN commercial_reservation.catalog_service s ON s.tenant_id = r.tenant_id AND s.service_id = r.service_id "
            + "JOIN commercial_reservation.salida_capacity d ON d.tenant_id = r.tenant_id AND d.salida_id = r.salida_id "
            + "WHERE r.tenant_id = ?";
    private static final String NOT_ARCHIVED = " AND r.reservation_status NOT IN ('COMPLETED', 'CANCELLED')";
    private final JdbcTemplate jdbc;
    private final JdbcReservationReader reader;

    public JdbcReservationQueries(JdbcTemplate jdbc, JdbcReservationReader reader) {
        this.jdbc = jdbc;
        this.reader = reader;
    }

    @Override
    public ReservationPage page(UUID tenantId, boolean includeArchived, int page, int limit) {
        return list(tenantId, null, includeArchived, page, limit);
    }

    @Override
    public Optional<Reservation> findByTenantAndId(UUID tenantId, UUID reservationId) {
        return detail(tenantId, null, reservationId);
    }

    // HU-COM-008 (AC-08.7, AC-08.15): the same statements with the customer of the account as one more condition, never a
    // filter applied afterwards; a reservation of another customer is the same empty answer as a missing one.
    @Override
    public ReservationPage page(UUID tenantId, UUID customerId, boolean includeArchived, int page, int limit) {
        return list(tenantId, customerId, includeArchived, page, limit);
    }

    @Override
    public Optional<Reservation> findByTenantCustomerAndId(UUID tenantId, UUID customerId, UUID reservationId) {
        return detail(tenantId, customerId, reservationId);
    }

    // The customer, when given, is one more condition of the same statements and never a filter applied afterwards.
    ReservationPage list(UUID tenantId, UUID customerId, boolean includeArchived, int page, int limit) {
        String where = FROM + (customerId == null ? "" : " AND r.customer_id = ?") + (includeArchived ? "" : NOT_ARCHIVED);
        List<Object> arguments = new ArrayList<>(List.of(tenantId));
        if (customerId != null) arguments.add(customerId);
        long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, arguments.toArray());
        arguments.add(limit);
        arguments.add((page - 1L) * limit);
        List<ReservationListItem> items = jdbc.query("SELECT r.reservation_id, c.full_name, s.commercial_name, d.hora_inicio, "
                + "r.reservation_status, r.payment_status, r.pending_balance_cents, r.payment_type, r.payment_method, "
                + "r.final_value_cents - r.pending_balance_cents AS validated, r.cancellation_cause, (SELECT a.validation_status FROM commercial_reservation.payment_attempt a "
                + "WHERE a.tenant_id = r.tenant_id AND a.reservation_id = r.reservation_id ORDER BY a.registered_at DESC LIMIT 1) AS latest_proof" + where
                + " ORDER BY r.created_at DESC, r.reservation_id DESC LIMIT ? OFFSET ?",
                (row, index) -> new ReservationListItem(row.getObject("reservation_id", UUID.class), row.getString("full_name"),
                        row.getString("commercial_name"), row.getObject("hora_inicio", OffsetDateTime.class).toInstant(),
                        ReservationStatus.valueOf(row.getString("reservation_status")),
                        PaymentStatus.valueOf(row.getString("payment_status")), new Cents(row.getLong("pending_balance_cents")),
                        PaymentType.valueOf(row.getString("payment_type")), PaymentMethod.valueOf(row.getString("payment_method")),
                        new Cents(row.getLong("validated")), Optional.ofNullable(row.getString("latest_proof")).map(ValidationStatus::valueOf),
                        Optional.ofNullable(row.getString("cancellation_cause")).map(CancellationCause::valueOf)),
                arguments.toArray());
        return new ReservationPage(items, total);
    }

    Optional<Reservation> detail(UUID tenantId, UUID customerId, UUID reservationId) {
        List<Object> arguments = new ArrayList<>(List.of(tenantId, reservationId));
        if (customerId != null) arguments.add(customerId);
        return jdbc.query("SELECT r.reservation_status, r.payment_status, r.pending_balance_cents, r.cancellation_cause, EXISTS (SELECT 1 FROM "
                + "commercial_reservation.capacity_allocation a WHERE a.tenant_id = r.tenant_id AND a.reservation_id = r.reservation_id "
                + "AND a.released_at IS NULL AND a.allocation_kind = 'RESERVATION') AS seat_held FROM commercial_reservation.reservation r "
                + "WHERE r.tenant_id = ? AND r.reservation_id = ?" + (customerId == null ? "" : " AND r.customer_id = ?"),
                (row, index) -> new Stored(ReservationStatus.valueOf(row.getString("reservation_status")),
                        PaymentStatus.valueOf(row.getString("payment_status")), new Cents(row.getLong("pending_balance_cents")),
                        row.getBoolean("seat_held"), Optional.ofNullable(row.getString("cancellation_cause")).map(CancellationCause::valueOf).orElse(null)),
                arguments.toArray()).stream().findFirst()
                .flatMap(stored -> reader.findAsCreated(tenantId, reservationId).map(created -> created.restoredAs(
                        stored.status(), stored.payment(), stored.pending(), stored.seatHeld(), new JdbcPaymentAttempts(jdbc).read(tenantId, reservationId))
                        .restoredCancellation(stored.cause())));
    }

    private record Stored(ReservationStatus status, PaymentStatus payment, Cents pending, boolean seatHeld, CancellationCause cause) {
    }
}
