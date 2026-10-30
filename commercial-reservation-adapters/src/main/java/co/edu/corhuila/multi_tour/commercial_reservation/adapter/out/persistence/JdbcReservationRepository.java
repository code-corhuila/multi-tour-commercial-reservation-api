package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FixedDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationCreated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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

    public JdbcReservationRepository(JdbcTemplate jdbc, FieldCipher cipher, TransactionTemplate transactions,
            ReservationCreatedOutbox outbox) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.transactions = transactions;
        this.outbox = outbox;
    }

    @Override
    public void save(Reservation reservation) {
        transactions.executeWithoutResult(status -> {
            insertReservation(reservation);
            insertReservedService(reservation);
            reservation.fixedDiscounts().forEach(line -> insertFixedDiscount(reservation, line));
            reservation.companions().forEach(companion -> insertCompanion(reservation, companion));
            outbox.append(ReservationCreated.of(reservation));
        });
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
