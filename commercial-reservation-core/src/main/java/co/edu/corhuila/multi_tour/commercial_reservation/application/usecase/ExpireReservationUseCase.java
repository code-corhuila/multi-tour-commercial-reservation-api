package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExpiryResult;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExpireReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder.Origin;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityReleased;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExpiryVerdict;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// RN-06, RN-07, RN-10; AC-11.2, AC-11.14: the job asks, the service re-checks under the row lock and decides. The cancellation, the
// seat release, the events and the handover of paid values commit together; a second call finds a cancelled reservation.
public class ExpireReservationUseCase implements ExpireReservation {

    private final ReservationRepository reservations;
    private final PaymentDeadlineReader deadlines;
    private final DepartureReader departures;
    private final SalidaCapacityHolder holder;
    private final CancelledValueRecorder cancelledValues;
    private final PaymentEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public ExpireReservationUseCase(ReservationRepository reservations, PaymentDeadlineReader deadlines, DepartureReader departures,
            SalidaCapacityHolder holder, CancelledValueRecorder cancelledValues, PaymentEventOutbox outbox, PaymentTransaction transaction,
            Clock clock) {
        this.reservations = reservations;
        this.deadlines = deadlines;
        this.departures = departures;
        this.holder = holder;
        this.cancelledValues = cancelledValues;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public ExpiryResult expire(UUID tenantId, UUID reservationId) {
        return transaction.inTransaction(() -> {
            Reservation reservation = reservations.lockByTenantAndId(tenantId, reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(reservationId));
            Instant now = Instant.now(clock);
            List<PaymentDeadline> rows = deadlines.openDeadlines(tenantId, reservationId);
            if (rows.isEmpty()) return refused(reservation, reservation.reservationStatus() == ReservationStatus.CANCELLED
                    ? ExpiryVerdict.ALREADY_CANCELLED : ExpiryVerdict.NOT_DUE);
            Instant start = departures.find(tenantId, reservation.departureId()).map(DepartureState::startsAt).orElse(null);
            // Any row that is reached, unpaid, not frozen and without a proof in time expires the reservation; a row still running never does.
            // When none does, the answer is the first reason that is not NOT_DUE (a proof, a freeze, a payment), else NOT_DUE.
            ExpiryVerdict verdict = ExpiryVerdict.NOT_DUE;
            for (PaymentDeadline row : rows) {
                ExpiryVerdict each = reservation.expiryVerdict(row.kind(), row.effective(start), row.frozen(), now);
                if (each == ExpiryVerdict.EXPIRABLE) { verdict = each; break; }
                if (verdict == ExpiryVerdict.NOT_DUE) verdict = each;
            }
            if (verdict != ExpiryVerdict.EXPIRABLE) return refused(reservation, verdict);
            Cents validated = new Cents(reservation.finalValue().amount() - reservation.pendingBalance().amount());
            int released = holder.release(tenantId, reservationId);
            if (released > 0) reservation.releaseSeat(CapacityReleased.CANCELLED_FOR_NON_PAYMENT, now);
            reservation.cancel(CancellationCause.PAYMENT_EXPIRY, null, now, released);
            reservations.save(reservation);
            reservation.pullEvents().forEach(outbox::append);
            if (validated.amount() > 0) cancelledValues.record(tenantId, reservationId, validated, Origin.PENDING_TREATMENT_AFTER_EXPIRY);
            return new ExpiryResult(reservationId, true, null, CancellationCause.PAYMENT_EXPIRY, ReservationStatus.CANCELLED);
        });
    }

    private static ExpiryResult refused(Reservation reservation, ExpiryVerdict reason) {
        return new ExpiryResult(reservation.reservationId(), false, reason, null, reservation.reservationStatus());
    }
}
