package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.time.Instant;
import java.util.OptionalInt;
import java.util.UUID;

// HU-COM-012 plan D-06: the deadline of kind PENDING_DIFFERENCE that an increase on a confirmed reservation creates. The advance hours come
// from the payment condition of the type (balance deadline); empty when none is configured.
public interface PendingDifferenceDeadlines {

    OptionalInt balanceAdvanceHours(UUID tenantId, UUID serviceId, PaymentType type);

    void record(UUID tenantId, UUID reservationId, Instant dueAt);
}
