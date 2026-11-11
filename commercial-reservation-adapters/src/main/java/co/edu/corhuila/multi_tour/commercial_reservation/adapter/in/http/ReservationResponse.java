package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NextCustomerAction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

// What was fixed at creation, read back unchanged (AC-07.10): no companions, no sensitive data, no tenant. The validated value is absent
// until one is held (AC-07.17); paymentDeadline is the deadline in force, absent when there is no open deadline (HU-COM-008).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationResponse(UUID reservationId, UUID customerId, UUID serviceId, UUID salidaId, int people,
        String reservationStatus, String paymentStatus, String paymentType, String paymentMethod,
        ReservationQuoteResponse.Line tourLine, ReservationQuoteResponse.Line transportLine, long projectedValueCents,
        List<ReservationQuoteResponse.DiscountLine> discounts, long finalValueCents, long pendingBalanceCents,
        boolean seatHeld, String createdAt, Long validatedValueCents, String proofStatus, String continueAction, String cancellationCause,
        String paymentDeadline) {

    // AC-07.17: the validated value is absent until one is held; it is the stored final value minus the pending balance.
    private static Long validated(Reservation reservation) {
        long held = reservation.finalValue().amount() - reservation.pendingBalance().amount();
        return held > 0 ? held : null;
    }

    public static ReservationResponse from(Reservation reservation, NextCustomerAction action, Optional<Instant> deadline) {
        var service = reservation.service();
        int people = reservation.people();
        return new ReservationResponse(reservation.reservationId(), reservation.customerId(), service.serviceId(),
                reservation.departureId(), people, reservation.reservationStatus().name(), reservation.paymentStatus().name(),
                reservation.expectedPaymentType().name(), reservation.expectedPaymentMethod().name(),
                ReservationQuoteResponse.Line.of(service.baseRatePerPerson(), people, service.tourLine(people)),
                service.transportRatePerPerson() == null ? null : ReservationQuoteResponse.Line.of(
                        service.transportRatePerPerson(), people, service.transportLine(people)),
                reservation.projectedValue().amount(), ReservationQuoteResponse.lines(reservation.fixedDiscounts()),
                reservation.finalValue().amount(), reservation.pendingBalance().amount(), reservation.seatHeld(),
                reservation.createdAt().toString(),
                validated(reservation),
                reservation.attempts().stream().max(Comparator.comparing(PaymentAttempt::registeredAt))
                        .map(PaymentAttempt::validationStatus).map(Enum::name).orElse(null),
                action.name(), reservation.cancellationCause() == null ? null : reservation.cancellationCause().name(),
                deadline.map(Instant::toString).orElse(null));
    }
}
