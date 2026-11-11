package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// What a transfer and a cash payment check before they write, in the order of plan D-06: the reservation of the tenant, the
// enabled type and method, the departure (started or cancelled), the state and the deadline (RN-03, RN-07, RN-17). It
// also holds the seats of the first payment (RN-06), which the caller runs inside its transaction.
final class PaymentPreconditions {

    private final ReservationRepository reservations;
    private final DepartureReader departures;
    private final PaymentDeadlineReader deadlines;
    private final EnabledPaymentOptions options;
    private final PaymentConditionReader conditions;
    private final SalidaCapacityHolder holder;
    private final Clock clock;

    PaymentPreconditions(ReservationRepository reservations, DepartureReader departures, PaymentDeadlineReader deadlines,
            EnabledPaymentOptions options, PaymentConditionReader conditions, SalidaCapacityHolder holder, Clock clock) {
        this.reservations = reservations;
        this.departures = departures;
        this.deadlines = deadlines;
        this.options = options;
        this.conditions = conditions;
        this.holder = holder;
        this.clock = clock;
    }

    Instant now() {
        return Instant.now(clock);
    }

    // Another tenant's reservation looks missing (AC-09.18).
    Reservation requireOpenFor(UUID tenantId, UUID reservationId, PaymentMethod method) {
        Reservation reservation = reservations.findByTenantAndId(tenantId, reservationId)
                .filter(found -> found.tenantId().equals(tenantId))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (!options.isEnabled(tenantId, reservation.service().serviceId(), reservation.expectedPaymentType(), method)) {
            throw new DomainException("paymentMethod", "the payment type or method is not enabled");
        }
        DepartureState departure = departures.find(tenantId, reservation.departureId())
                .filter(found -> found.tenantId().equals(tenantId))
                .orElseThrow(() -> new DepartureNotFoundException(reservation.departureId()));
        if (departure.status() == DepartureStatus.CANCELLED) throw new BusinessRuleViolation("the departure was cancelled");
        if (!now().isBefore(departure.startsAt())) throw new BusinessRuleViolation("the departure has already started");
        reservation.requireOpenToPayment();
        deadlines.openDeadline(tenantId, reservationId).ifPresent(deadline -> {
            if (!now().isBefore(deadline.dueAt())) throw new BusinessRuleViolation("the payment deadline has expired");
        });
        return reservation;
    }

    // Only a deposit has a minimum; a deposit without a configured one cannot be judged (DP-11).
    Cents minimumFor(Reservation reservation) {
        if (reservation.expectedPaymentType() != PaymentType.DEPOSIT) return null;
        return conditions.minimumDepositFor(reservation.tenantId(), reservation)
                .orElseThrow(() -> new BusinessRuleViolation("the minimum deposit is not configured"));
    }

    // RN-06 and RN-16: only a reservation with no seat held asks the capacity row; the loser gets the answer of a full departure.
    void holdFirstSeat(Reservation reservation) {
        if (reservation.seatHeld()) return;
        if (!holder.hold(reservation.tenantId(), reservation.departureId(), reservation.people())) {
            throw new BusinessRuleViolation("the departure has no availability");
        }
        reservation.allocateSeat(now());
    }
}
