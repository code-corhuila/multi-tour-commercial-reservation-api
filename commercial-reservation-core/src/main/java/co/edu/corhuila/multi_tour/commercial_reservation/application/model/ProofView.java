package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SuspensionIntervals;
import java.time.Instant;
import java.util.UUID;

// One attempt as the review and the history show it: the declared amount, what the reviewer set, the decision and the 24 hour
// mark derived from the clock (AC-10.6, AC-10.14). Neither the file nor its name is part of it. The customer display name of
// the list (HU-COM-005) is read by the adapter and is not part of this view. `expectedMethod` is the method the reservation expects,
// not the method of the attempt, so the panel row needs no read of the reservation.
public record ProofView(UUID reservationId, UUID customerId, UUID attemptId, PaymentType paymentType, PaymentMethod method,
        Cents declaredAmount, Cents verifiedAmount, Cents appliedAmount, Cents excessAmount, Cents treatmentAmount,
        ValidationStatus status, Instant presentedAt, Instant reviewDueAt, boolean reviewOverdue, UUID registeredBy, UUID decidedBy,
        Instant decidedAt, String decisionReason, Cents pendingBalance, ReservationStatus reservationStatus,
        PaymentStatus paymentStatus, PaymentMethod expectedMethod) {

    public static ProofView of(Reservation reservation, PaymentAttempt attempt, Instant now) {
        return of(reservation, attempt, now, new SuspensionIntervals(java.util.List.of()));
    }

    public static ProofView of(Reservation reservation, PaymentAttempt attempt, Instant now, SuspensionIntervals suspensions) {
        boolean transfer = attempt.method() == PaymentMethod.TRANSFER;
        return new ProofView(reservation.reservationId(), reservation.customerId(), attempt.attemptId(), reservation.expectedPaymentType(),
                attempt.method(), attempt.amount(), attempt.verifiedAmount(), attempt.appliedAmount(), attempt.excessAmount(),
                attempt.treatmentAmount(), attempt.validationStatus(), attempt.registeredAt(), transfer ? attempt.reviewDueAt(suspensions).orElse(null) : null,
                attempt.reviewOverdue(now, suspensions), attempt.registeredBy(), attempt.decidedBy(), attempt.decidedAt(), attempt.decisionReason(),
                reservation.pendingBalance(), reservation.reservationStatus(), reservation.paymentStatus(), reservation.expectedPaymentMethod());
    }
}
