package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.Instant;
import java.util.UUID;

// What the refund determination reads of a reservation (spec 026): the validated value (final value minus the pending balance, so values in validation do not
// count, RN-RES-006A), the people, the service and the start of its departure (null when the mirror has no row).
public record RefundBasis(UUID reservationId, Cents validated, int people, UUID serviceId, Instant departureStart) {}
