package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationQuote.Reason;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteModification;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// AC-12.2, AC-12.10: the terms are read first; when they allow the change, the aggregate computes it without keeping it. A reservation of
// another tenant looks missing.
public class QuoteModificationUseCase implements QuoteModification {

    private final ReservationRepository reservations;
    private final DepartureReader departures;
    private final ChangePolicyReader policies;
    private final Clock clock;

    public QuoteModificationUseCase(ReservationRepository reservations, DepartureReader departures, ChangePolicyReader policies, Clock clock) {
        this.reservations = reservations;
        this.departures = departures;
        this.policies = policies;
        this.clock = clock;
    }

    @Override
    public ModificationQuote quote(PaymentActor actor, UUID reservationId, ModificationRequest asked) {
        ModificationAuthorization.requireWrite(actor);
        Reservation reservation = reservations.findByTenantAndId(actor.tenantId(), reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        ModificationRequest request = ResolvedChange.of(reservation, asked);
        Instant now = Instant.now(clock);
        Instant start = departures.find(reservation.tenantId(), reservation.departureId()).map(DepartureState::startsAt).orElse(null);
        Reason reason = ChangeTerms.refusal(
                policies.policyFor(reservation.tenantId(), reservation.service().serviceId(), request.changeType()), start, now);
        if (reason != null) return new ModificationQuote(reservationId, false, reason, null, true, reservation.projectedValue(),
                new Cents(reservation.projectedValue().amount() - reservation.finalValue().amount()));
        var outcome = reservation.quoteModification(request.partySize(), reservation.service(), request.departureId(), request.companions(), start, now);
        boolean moves = request.changeType() == ChangeType.DATE_CHANGE;
        int needed = reservation.seatHeld() && !moves ? Math.max(outcome.seatDelta(), 0) : request.partySize();
        boolean fits = departures.find(reservation.tenantId(), request.departureId()).map(row -> row.seats().available() >= needed).orElse(false);
        Cents projected = reservation.service().projectedFor(request.partySize());
        return new ModificationQuote(reservationId, fits, fits ? null : Reason.NO_AVAILABILITY, outcome, fits, projected,
                new Cents(projected.amount() - outcome.newFinalValue().amount()));
    }
}
