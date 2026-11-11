package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

// What was fixed at creation, read back unchanged (AC-07.10): no companions, no sensitive data, no tenant. The validated value, the
// deadline and the conditions are absent until the story that holds them (AC-07.17): the seat is not held at creation.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationResponse(UUID reservationId, UUID customerId, UUID serviceId, UUID salidaId, int people,
        String reservationStatus, String paymentStatus, String paymentType, String paymentMethod,
        ReservationQuoteResponse.Line tourLine, ReservationQuoteResponse.Line transportLine, long projectedValueCents,
        List<ReservationQuoteResponse.DiscountLine> discounts, long finalValueCents, long pendingBalanceCents,
        boolean seatHeld, String createdAt) {

    public static ReservationResponse from(Reservation reservation) {
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
                reservation.createdAt().toString());
    }
}
