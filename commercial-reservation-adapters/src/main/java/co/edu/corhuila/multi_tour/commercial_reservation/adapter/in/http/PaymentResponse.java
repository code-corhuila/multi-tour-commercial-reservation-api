package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

// A payment attempt with the state of the reservation after it. Neither the file nor its name is returned; the review fields are
// absent until HU-COM-010 decides the proof (AC-09.16).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(UUID paymentId, UUID reservationId, String paymentMethod, long amountCents, String validationStatus,
        UUID registeredBy, String registeredAt, String reservationStatus, String paymentStatus, long pendingBalanceCents,
        boolean seatHeld, String paymentDeadline, UUID decidedBy, String decidedAt, String decisionReason, Long verifiedAmountCents) {

    // The due instant of the open deadline, absent while the reservation has none (the quote carries the same value).
    static PaymentResponse of(Reservation reservation, PaymentAttempt attempt, java.time.Instant dueAt) {
        return new PaymentResponse(attempt.attemptId(), reservation.reservationId(), attempt.method().name(), attempt.amount().amount(),
                attempt.validationStatus() == null ? null : attempt.validationStatus().name(), attempt.registeredBy(),
                attempt.registeredAt().toString(), reservation.reservationStatus().name(), reservation.paymentStatus().name(),
                reservation.pendingBalance().amount(), reservation.seatHeld(), dueAt == null ? null : dueAt.toString(), attempt.decidedBy(),
                attempt.decidedAt() == null ? null : attempt.decidedAt().toString(), attempt.decisionReason(),
                attempt.verifiedAmount() == null ? null : attempt.verifiedAmount().amount());
    }
}
