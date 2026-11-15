package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationQuote.Refusal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReprogrammingMarkReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationSettlement;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// AC-13.3, AC-13.2, AC-13.6: the same settlement the cancellation would fix, read without any write. A reservation of another tenant
// looks missing (AC-13.9).
public class QuoteCancellationUseCase implements QuoteCancellation {

    private final ReservationRepository reservations;
    private final DepartureReader departures;
    private final CancellationPolicyReader policy;
    private final ReprogrammingMarkReader marks;
    private final Clock clock;

    public QuoteCancellationUseCase(ReservationRepository reservations, DepartureReader departures, CancellationPolicyReader policy,
            ReprogrammingMarkReader marks, Clock clock) {
        this.reservations = reservations;
        this.departures = departures;
        this.policy = policy;
        this.marks = marks;
        this.clock = clock;
    }

    @Override
    public CancellationQuote quote(PaymentActor actor, UUID reservationId) {
        CancellationAuthorization.requireCancel(actor);
        Reservation reservation = reservations.findByTenantAndId(actor.tenantId(), reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        CancellationSettlement settlement = settle(policy, departures, reservation, Instant.now(clock));
        Refusal refusal = !isCancellableStatus(reservation) ? Refusal.INVALID_STATUS_TRANSITION
                : marks.isMarked(reservation.tenantId(), reservationId) ? Refusal.BUSINESS_RULE_VIOLATION : null;
        return new CancellationQuote(reservationId, refusal == null, refusal, settlement);
    }

    static boolean isCancellableStatus(Reservation reservation) {
        return reservation.reservationStatus() == ReservationStatus.PENDING_PAYMENT
                || reservation.reservationStatus() == ReservationStatus.CONFIRMED;
    }

    // Validated = what was paid and validated: the final value minus the balance still pending (values in validation do not count).
    static CancellationSettlement settle(CancellationPolicyReader policy, DepartureReader departures, Reservation reservation, Instant now) {
        Cents validated = new Cents(reservation.finalValue().amount() - reservation.pendingBalance().amount());
        Instant start = departures.find(reservation.tenantId(), reservation.departureId()).map(DepartureState::startsAt).orElse(null);
        return CancellationSettlement.of(validated,
                policy.penaltyBasisPoints(reservation.tenantId(), reservation.service().serviceId(), start, now));
    }
}
