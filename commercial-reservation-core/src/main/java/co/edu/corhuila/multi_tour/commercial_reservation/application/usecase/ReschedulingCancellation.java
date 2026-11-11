package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityReleased;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.UUID;

// HU-COM-018 RN-06, AC-18.7: the cancellation that every way of not accepting a rescheduling shares. The hold of a pending proposal and the seats of the reservation are released, the reservation is
// cancelled through its own cancellation with the cause NOT_ATTRIBUTABLE_TO_CUSTOMER, and a refund determination of 100 % of the validated values is left only when something was paid and validated.
// Called inside the transaction of the use case.
final class ReschedulingCancellation {

    private final ReservationRepository reservations;
    private final SalidaCapacityHolder holder;
    private final DetermineRefund refunds;
    private final PaymentEventOutbox outbox;

    ReschedulingCancellation(ReservationRepository reservations, SalidaCapacityHolder holder, DetermineRefund refunds, PaymentEventOutbox outbox) {
        this.reservations = reservations;
        this.holder = holder;
        this.refunds = refunds;
        this.outbox = outbox;
    }

    void cancel(UUID tenantId, Reservation reservation, UUID by, Instant now, boolean releaseHold) {
        if (releaseHold) holder.releaseReschedulingHold(tenantId, reservation.reservationId());
        int seats = reservation.seatHeld() ? holder.release(tenantId, reservation.reservationId()) : 0;
        if (seats > 0) reservation.releaseSeat(CapacityReleased.DEPARTURE_CANCELLED_OR_INTERRUPTED, now);
        long paid = reservation.finalValue().amount() - reservation.pendingBalance().amount();
        reservation.cancel(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER, by, now, seats);
        reservations.save(reservation);
        reservation.pullEvents().forEach(outbox::append);
        if (paid > 0) refunds.determine(tenantId, reservation.reservationId(), RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, null);
    }
}
