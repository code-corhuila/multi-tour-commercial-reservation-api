package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityAllocated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityReleased;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticePolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationCancelled;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvidenceRegistered;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentRejected;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentValidated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationConfirmed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The outbox of the payment family, written inside the transaction of the change. The payload keys are the closed lists of the
// database (-db 015 and 017): references, amounts and codes, never a file, a file name, bank data or a person. The events carry no
// actor, but the outbox requires one: it is the user of the latest attempt of the reservation (who decided it, else who registered it).
public final class JdbcPaymentEventOutbox implements PaymentEventOutbox {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final ClientNoticeOutbox notices;

    public JdbcPaymentEventOutbox(JdbcTemplate jdbc) {
        this(jdbc, (tenantId, reservationId, type, triggeringEventType, triggeringEventId, occurredAt) -> { });
    }

    // The notices of RF-019 are written right after the event that triggers them, in the same transaction (HU-COM-015 plan D-03).
    public JdbcPaymentEventOutbox(JdbcTemplate jdbc, ClientNoticeOutbox notices) {
        this.jdbc = jdbc;
        this.notices = notices;
    }

    @Override
    public void append(PaymentEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", event.reservationId().toString());
        switch (event) {
            case PaymentEvidenceRegistered e -> {
                payload.put("attemptId", e.attemptId().toString());
                payload.put("modality", e.modality().name());
                payload.put("registeredAmountCents", e.registeredAmount().amount());
                payload.put("validationStatus", e.validationStatus() == null ? null : e.validationStatus().name());
            }
            case CapacityAllocated e -> {
                payload.put("serviceId", e.serviceId().toString());
                payload.put("salidaId", e.salidaId().toString());
                payload.put("allocationKind", e.allocationKind());
            }
            case PaymentValidated e -> {
                payload.put("amountCents", e.amount().amount());
                payload.put("paymentStatus", e.paymentStatus().name());
                payload.put("pendingBalanceCents", e.pendingBalance().amount());
                payload.put("validatorId", e.validatorId().toString());
            }
            case PaymentRejected e -> {
                payload.put("reason", e.reason());
                payload.put("pendingBalanceCents", e.pendingBalance().amount());
                payload.put("validatorId", e.validatorId().toString());
            }
            case ReservationConfirmed e -> {
                payload.put("paymentStatus", e.paymentStatus().name());
                payload.put("allocatedCapacity", e.allocatedCapacity());
            }
            case CapacityReleased e -> {
                payload.put("serviceId", e.serviceId().toString());
                payload.put("salidaId", e.salidaId().toString());
                payload.put("reason", e.reason());
            }
            case ReservationCancelled e -> {
                payload.put("cause", e.cause().name());
                payload.put("releasedSeats", e.releasedSeats());
            }
            default -> throw new IllegalArgumentException("not a payment event: " + event.getClass().getSimpleName());
        }
        try {
            UUID eventId = UUID.randomUUID();
            jdbc.update("INSERT INTO commercial_reservation.outbox_event (outbox_event_id, tenant_id, event_type, reservation_id, actor_id, occurred_at, "
                    + "payload) VALUES (?, ?, ?, ?, COALESCE((SELECT COALESCE(decided_by, registered_by) FROM "
                    + "commercial_reservation.payment_attempt WHERE reservation_id = ? ORDER BY COALESCE(decided_at, registered_at) DESC "
                    + "LIMIT 1), (SELECT created_by FROM commercial_reservation.reservation WHERE reservation_id = ?)), ?, ?::jsonb)",
                    eventId, event.tenantId(), event.getClass().getSimpleName(), event.reservationId(), event.reservationId(),
                    event.reservationId(), event.occurredAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC),
                    JSON.writeValueAsString(payload));
            notice(event, eventId);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("the event payload cannot be written", failure);
        }
    }

    // A validated payment notifies only when the latest attempt of the reservation is a transfer, which a person reviewed; cash recorded by staff does not (DP-15-03).
    private void notice(PaymentEvent event, UUID eventId) {
        boolean reviewedProof = event instanceof PaymentValidated && Boolean.TRUE.equals(jdbc.queryForObject("SELECT COALESCE((SELECT payment_method = 'TRANSFER' "
                + "FROM commercial_reservation.payment_attempt WHERE reservation_id = ? ORDER BY COALESCE(decided_at, registered_at) DESC LIMIT 1), false)",
                Boolean.class, event.reservationId()));
        NoticePolicy.of(event, reviewedProof).ifPresent(type -> notices.append(event.tenantId(), event.reservationId(), type,
                event.getClass().getSimpleName(), eventId, event.occurredAt()));
    }
}
