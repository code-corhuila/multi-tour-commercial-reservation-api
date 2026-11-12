package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FixedDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationCreated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

// The whole creation is one write (plan D-06): the reservation, its reserved service, the fixed discount lines, the companions
// with their sensitive data and the ReservationCreated event commit or roll back together. The transaction joins the one the
// caller already opened (the idempotency key shares it) and starts one when there is none. Sensitive values are sealed here, before
// the SQL, bound to tenant, companion and column; nothing sensitive is in the event or in any message.
public final class JdbcReservationRepository implements ReservationRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final FieldCipher cipher;
    private final TransactionTemplate transactions;
    private final ReservationCreatedOutbox outbox;
    private final JdbcReservationReader reader;
    private final JdbcPaymentAttempts attempts;

    public JdbcReservationRepository(JdbcTemplate jdbc, FieldCipher cipher, TransactionTemplate transactions,
            ReservationCreatedOutbox outbox) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.transactions = transactions;
        this.outbox = outbox;
        this.reader = new JdbcReservationReader(jdbc, cipher);
        this.attempts = new JdbcPaymentAttempts(jdbc);
    }

    @Override
    public void save(Reservation reservation) {
        transactions.executeWithoutResult(status -> {
            if (exists(reservation)) {
                updatePayment(reservation); // a payment or a decision changed the reservation that was created before
                syncModification(reservation);
            } else {
                insertReservation(reservation);
                insertReservedService(reservation);
                reservation.fixedDiscounts().forEach(line -> insertFixedDiscount(reservation, line));
                reservation.companions().forEach(companion -> insertCompanion(reservation, companion));
                outbox.append(ReservationCreated.of(reservation));
            }
            attempts.write(reservation);
            attempts.keepSeat(reservation);
        });
    }

    // Scoped to the tenant: another tenant's reservation is the same empty answer as a missing one.
    @Override
    public Optional<Reservation> findByTenantAndId(UUID tenantId, UUID reservationId) {
        return reader.findAsCreated(tenantId, reservationId).flatMap(created -> jdbc.query(
                "SELECT reservation_status, payment_status, pending_balance_cents FROM commercial_reservation.reservation "
                + "WHERE tenant_id = ? AND reservation_id = ?", (row, index) -> created.restoredAs(
                        ReservationStatus.valueOf(row.getString("reservation_status")), PaymentStatus.valueOf(row.getString("payment_status")),
                        new Cents(row.getLong("pending_balance_cents")), attempts.openSeat(tenantId, reservationId),
                        attempts.read(tenantId, reservationId)), tenantId, reservationId).stream().findFirst());
    }


    // The review list reads the reservations of a page together: the same restore as findByTenantAndId, in a fixed number of statements
    // (creation values, lines, companions, state, open seats, attempts) whatever the number of reservations. Tenant-scoped like the single read.
    @Override
    public java.util.Map<UUID, Reservation> findByTenantAndIds(UUID tenantId, java.util.Collection<UUID> reservationIds) {
        java.util.Map<UUID, Reservation> found = reader.findAllAsCreated(tenantId, reservationIds);
        if (found.isEmpty()) return found;
        String ids = SqlUuidArray.of(found.keySet());
        java.util.Set<UUID> holding = attempts.openSeats(tenantId, ids);
        java.util.Map<UUID, java.util.List<co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt>> stored =
                attempts.readAll(tenantId, ids);
        jdbc.query("SELECT reservation_id, reservation_status, payment_status, pending_balance_cents FROM commercial_reservation.reservation "
                + "WHERE tenant_id = ? AND reservation_id = ANY (?::uuid[])", (row, index) -> {
                    UUID id = row.getObject("reservation_id", UUID.class);
                    return found.get(id).restoredAs(ReservationStatus.valueOf(row.getString("reservation_status")),
                            PaymentStatus.valueOf(row.getString("payment_status")), new Cents(row.getLong("pending_balance_cents")),
                            holding.contains(id), stored.getOrDefault(id, java.util.List.of()));
                }, tenantId, ids);
        return found;
    }

    // HU-COM-011 RN-06: the same lookup after taking the row lock, so a second expiry waits and then finds the cancelled state. It runs
    // inside the transaction of the use case.
    @Override
    public Optional<Reservation> lockByTenantAndId(UUID tenantId, UUID reservationId) {
        jdbc.queryForList("SELECT 1 FROM commercial_reservation.reservation WHERE tenant_id = ? AND reservation_id = ? FOR UPDATE", tenantId,
                reservationId);
        return findByTenantAndId(tenantId, reservationId);
    }
    @Override
    public Optional<Reservation> findByTenantAndAttempt(UUID tenantId, UUID attemptId) {
        return jdbc.query("SELECT reservation_id FROM commercial_reservation.payment_attempt WHERE tenant_id = ? AND payment_attempt_id = ?",
                (row, index) -> row.getObject("reservation_id", UUID.class), tenantId, attemptId).stream().findFirst()
                .flatMap(reservationId -> findByTenantAndId(tenantId, reservationId));
    }

    // HU-COM-012: a modification changes the party, the departure, the value, the line amounts (the only columns of a line the writer may
    // update) and the companions (replaced). Run only when the stored reservation differs, so a payment does not rewrite them.
    private void syncModification(Reservation r) {
        var stored = jdbc.queryForMap("SELECT party_size, salida_id, final_value_cents FROM commercial_reservation.reservation "
                + "WHERE tenant_id = ? AND reservation_id = ?", r.tenantId(), r.reservationId());
        java.util.Set<String> documents = new java.util.HashSet<>(jdbc.queryForList("SELECT document_number FROM commercial_reservation.reservation_companion "
                + "WHERE tenant_id = ? AND reservation_id = ?", String.class, r.tenantId(), r.reservationId()));
        java.util.Set<String> wanted = new java.util.HashSet<>();
        r.companions().forEach(companion -> wanted.add(companion.documentNumber().value()));
        boolean changed = ((Number) stored.get("party_size")).intValue() != r.people() || !r.departureId().equals(stored.get("salida_id"))
                || ((Number) stored.get("final_value_cents")).longValue() != r.finalValue().amount();
        boolean companionsChanged = !documents.equals(wanted);
        if (!changed && !companionsChanged) return;
        jdbc.update("UPDATE commercial_reservation.reservation SET party_size = ?, salida_id = ?, projected_value_cents = ?, final_value_cents = ? "
                + "WHERE tenant_id = ? AND reservation_id = ?", r.people(), r.departureId(), r.projectedValue().amount(), r.finalValue().amount(),
                r.tenantId(), r.reservationId());
        jdbc.update("UPDATE commercial_reservation.reserved_service SET party_size = ? WHERE tenant_id = ? AND reservation_id = ?",
                r.people(), r.tenantId(), r.reservationId());
        r.fixedDiscounts().forEach(line -> jdbc.update("UPDATE commercial_reservation.reservation_fixed_discount SET base_cents = ?, amount_cents = ?, "
                + "subtotal_cents = ? WHERE tenant_id = ? AND reservation_id = ? AND line_order = ?", line.base().amount(), line.amount().amount(),
                line.subtotal().amount(), r.tenantId(), r.reservationId(), line.order()));
        jdbc.update("UPDATE commercial_reservation.capacity_allocation SET party_size = ?, salida_id = ? WHERE tenant_id = ? AND reservation_id = ? "
                + "AND released_at IS NULL AND allocation_kind = 'RESERVATION'", r.people(), r.departureId(), r.tenantId(), r.reservationId());
        if (companionsChanged) {
            jdbc.update("DELETE FROM commercial_reservation.reservation_companion_sensitive_data WHERE tenant_id = ? AND reservation_companion_id IN "
                    + "(SELECT reservation_companion_id FROM commercial_reservation.reservation_companion WHERE tenant_id = ? AND reservation_id = ?)",
                    r.tenantId(), r.tenantId(), r.reservationId());
            jdbc.update("DELETE FROM commercial_reservation.reservation_companion WHERE tenant_id = ? AND reservation_id = ?", r.tenantId(), r.reservationId());
            r.companions().forEach(companion -> insertCompanion(r, companion));
        }
    }

    private boolean exists(Reservation r) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.reservation WHERE tenant_id = ? AND reservation_id = ?",
                Integer.class, r.tenantId(), r.reservationId()) > 0;
    }

    private void updatePayment(Reservation r) {
        jdbc.update("UPDATE commercial_reservation.reservation SET reservation_status = ?, payment_status = ?, pending_balance_cents = ?, "
                + "cancellation_cause = ?, cancelled_at = ?, cancelled_by = ? "
                + "WHERE tenant_id = ? AND reservation_id = ? AND reservation_status <> 'CANCELLED'", r.reservationStatus().name(), r.paymentStatus().name(),
                r.pendingBalance().amount(), r.cancellationCause() == null ? null : r.cancellationCause().name(),
                r.cancelledAt() == null ? null : java.time.OffsetDateTime.ofInstant(r.cancelledAt(), java.time.ZoneOffset.UTC), r.cancelledBy(),
                r.tenantId(), r.reservationId());
    }

    private void insertReservation(Reservation r) {
        jdbc.update("INSERT INTO commercial_reservation.reservation (reservation_id, tenant_id, customer_id, salida_id, "
                + "service_id, party_size, projected_value_cents, final_value_cents, pending_balance_cents, "
                + "credit_balance_cents, reservation_status, payment_status, payment_type, payment_method, created_by, "
                + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", r.reservationId(), r.tenantId(),
                r.customerId(), r.departureId(), r.service().serviceId(), r.people(), r.projectedValue().amount(),
                r.finalValue().amount(), r.pendingBalance().amount(), r.creditBalance().amount(),
                r.reservationStatus().name(), r.paymentStatus().name(), r.expectedPaymentType().name(),
                r.expectedPaymentMethod().name(), r.createdBy(), timestamp(r.createdAt()));
    }

    // The date of the service is "when applicable" (models.md) and the reservation does not carry one: it stays null.
    private void insertReservedService(Reservation r) {
        ReservedService service = r.service();
        jdbc.update("INSERT INTO commercial_reservation.reserved_service (reserved_service_id, tenant_id, reservation_id, "
                + "service_id, party_size, scheduled_date, base_rate_cents, transport_rate_cents) "
                + "VALUES (?, ?, ?, ?, ?, null, ?, ?)", UUID.randomUUID(), r.tenantId(), r.reservationId(),
                service.serviceId(), r.people(), service.baseRatePerPerson().amount(),
                service.transportRatePerPerson() == null ? null : service.transportRatePerPerson().amount());
    }

    private void insertFixedDiscount(Reservation r, FixedDiscount line) {
        jdbc.update("INSERT INTO commercial_reservation.reservation_fixed_discount (reservation_fixed_discount_id, tenant_id, "
                + "reservation_id, line_order, discount_rule_id, rule_name, discount_type, discount_percentage_bp, "
                + "discount_amount_cents, calculation_base, base_cents, amount_cents, subtotal_cents, cap_cents) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), r.tenantId(), r.reservationId(),
                line.order(), line.ruleId(), line.ruleName(), line.type().name(), line.percentageBasisPoints(),
                line.amountCents(), line.calculationBase().name(), line.base().amount(), line.amount().amount(),
                line.subtotal().amount(), line.capCents());
    }

    private void insertCompanion(Reservation r, Companion companion) {
        UUID id = UUID.randomUUID();
        UUID tenant = r.tenantId();
        jdbc.update("INSERT INTO commercial_reservation.reservation_companion (reservation_companion_id, tenant_id, "
                + "reservation_id, full_name, document_number, birth_date_or_age) VALUES (?, ?, ?, ?, ?, ?)", id, tenant,
                r.reservationId(), companion.fullName(), companion.documentNumber().value(),
                cipher.encrypt(birth(companion.birthDateOrAge()), aad(tenant, id, "birth_date_or_age")));
        if (companion.highRiskData() != null) {
            String envelope = cipher.encrypt(risk(companion.highRiskData()), aad(tenant, id, "activity_requirements"));
            jdbc.update("INSERT INTO commercial_reservation.reservation_companion_sensitive_data (reservation_companion_id, "
                    + "tenant_id, activity_requirements) VALUES (?, ?, ?::jsonb)", id, tenant,
                    "{\"envelope\":\"" + envelope + "\"}");
        }
    }

    private static String aad(UUID tenant, UUID companion, String column) {
        return tenant + "|" + companion + "|" + column;
    }

    private static String birth(BirthDateOrAge value) {
        return value.birthDate() != null ? "D:" + value.birthDate() : "A:" + value.age();
    }

    private static String risk(HighRiskData value) {
        var body = new LinkedHashMap<String, Object>();
        body.put("bloodType", value.bloodType());
        body.put("emergencyContact", value.emergencyContact());
        body.put("mobilityRestrictions", value.mobilityRestrictions());
        body.put("informedConsentSigned", value.informedConsentSigned());
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("a companion value could not be serialized");
        }
    }

    private static OffsetDateTime timestamp(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
