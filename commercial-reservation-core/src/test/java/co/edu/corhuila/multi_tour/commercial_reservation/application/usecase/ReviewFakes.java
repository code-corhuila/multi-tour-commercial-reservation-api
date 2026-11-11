package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// The doubles of HU-COM-011 (the cancellation, which owns the status change and ReservationCancelled) and HU-COM-016 (the
// treatment of the value of a cancelled reservation). They stay doubles until those stories exist (declared debt DD-5, DD-9).
final class ReviewFakes implements ReservationCancellation, CancelledValueRecorder {

    private final PaymentFakes store;
    final List<UUID> cancelled = new ArrayList<>();
    final List<String> values = new ArrayList<>();

    ReviewFakes(PaymentFakes store) {
        this.store = store;
    }

    @Override
    public void cancelForNonPayment(UUID tenantId, UUID reservationId, Instant now) {
        cancelled.add(reservationId);
        Reservation reservation = store.reservations.get(reservationId);
        store.reservations.put(reservationId, reservation.restoredAs(ReservationStatus.CANCELLED, reservation.paymentStatus(),
                reservation.pendingBalance(), reservation.seatHeld(), reservation.attempts()));
    }

    @Override
    public void record(UUID tenantId, UUID reservationId, Cents value, Origin origin) {
        values.add(origin + ":" + value.amount());
    }
}
