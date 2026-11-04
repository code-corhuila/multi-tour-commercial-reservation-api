package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.time.Instant;
import java.util.UUID;

// A row of the pending-payments screen. The customer name is read from the customer record and is never logged. The effective
// deadline is derived by the query at read time (AC-11.6); hasProofInValidation lets the portal leave those rows to HU-COM-010.
public record PendingPaymentRow(UUID reservationId, String customerName, PaymentType paymentType, PaymentMethod paymentMethod,
        long validatedValueCents, long pendingBalanceCents, PaymentStatus paymentStatus, DeadlineKind deadlineKind, Instant effectiveDeadline,
        boolean hasProofInValidation) {
}
