package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// The seats of a reservation go back to the departure: the rejection of the first payment (RN-RES-007) or a cancellation for expiry.
public record CapacityReleased(UUID tenantId, UUID reservationId, UUID serviceId, UUID salidaId, String reason,
        Instant occurredAt) implements PaymentEvent {
    public static final String FIRST_PAYMENT_REJECTED = "FIRST_PAYMENT_REJECTED";
    public static final String CANCELLED_FOR_NON_PAYMENT = "CANCELLED_FOR_NON_PAYMENT";
    public static final String CANCELLED_AT_CUSTOMER_REQUEST = "CANCELLED_AT_CUSTOMER_REQUEST";
    public static final String RESERVATION_MODIFIED = "RESERVATION_MODIFIED";
}
