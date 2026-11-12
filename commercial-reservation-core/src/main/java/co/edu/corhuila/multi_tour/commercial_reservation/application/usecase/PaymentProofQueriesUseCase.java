package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadPaymentProofs;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SuspensionIntervals;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

// One proof and the history of a reservation with every attempt, the rejected ones included. Another tenant's reservation or
// proof looks missing (AC-10.17, AC-09.18), and so does another customer's reservation.
public class PaymentProofQueriesUseCase implements ReadPaymentProofs {

    private final ReservationRepository reservations;
    private final Function<UUID, SuspensionIntervals> suspensions;
    private final Clock clock;

    public PaymentProofQueriesUseCase(ReservationRepository reservations, Clock clock) {
        this(reservations, tenant -> new SuspensionIntervals(List.of()), clock);
    }

    public PaymentProofQueriesUseCase(ReservationRepository reservations, Function<UUID, SuspensionIntervals> suspensions, Clock clock) {
        this.reservations = reservations;
        this.suspensions = suspensions;
        this.clock = clock;
    }

    @Override
    public ProofView get(PaymentActor actor, UUID attemptId) {
        Reservation reservation = reservations.findByTenantAndAttempt(actor.tenantId(), attemptId)
                .filter(found -> found.tenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
        PaymentProofAuthorization.requireRead(actor);
        Instant now = Instant.now(clock);
        return reservation.attempts().stream().filter(attempt -> attempt.attemptId().equals(attemptId)).findFirst()
                .map(attempt -> ProofView.of(reservation, attempt, now, suspensions.apply(actor.tenantId()))).orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
    }

    @Override
    public List<ProofView> history(PaymentActor actor, UUID reservationId) {
        Reservation reservation = reservations.findByTenantAndId(actor.tenantId(), reservationId)
                .filter(found -> found.tenantId().equals(actor.tenantId()))
                .filter(found -> !actor.role().equals(PaymentActor.Role.FINAL_CUSTOMER) || PaymentProofAuthorization.isOwner(actor, found))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (!PaymentProofAuthorization.isOwner(actor, reservation)) PaymentProofAuthorization.requireRead(actor);
        Instant now = Instant.now(clock);
        return reservation.attempts().stream().map(attempt -> ProofView.of(reservation, attempt, now, suspensions.apply(actor.tenantId()))).toList();
    }
}
