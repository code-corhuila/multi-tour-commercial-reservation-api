package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofView;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

// A proof as the review panel shows it: the declared, verified, applied, excess and treatment amounts, the decision and the 24 hour
// mark. The mark is derived at read time; the file and its name are never part of it (AC-10.6, AC-10.19).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProofResponse(UUID paymentId, UUID reservationId, String customerName, String paymentType, long declaredAmountCents,
        Long verifiedAmountCents, String validationStatus, String presentedAt, String reviewDueAt, boolean reviewOverdue,
        long pendingBalanceCents, Long appliedAmountCents, Long excessAmountCents, Long treatmentAmountCents, UUID decidedBy,
        String decidedAt, String decisionReason, String reservationStatus, String paymentStatus) {

    static ProofResponse of(ProofView view, String customerName) {
        return new ProofResponse(view.attemptId(), view.reservationId(), customerName, view.paymentType().name(),
                view.declaredAmount().amount(), cents(view.verifiedAmount()), view.status() == null ? null : view.status().name(),
                view.presentedAt().toString(), view.reviewDueAt() == null ? null : view.reviewDueAt().toString(), view.reviewOverdue(),
                view.pendingBalance().amount(), cents(view.appliedAmount()), cents(view.excessAmount()), cents(view.treatmentAmount()),
                view.decidedBy(), view.decidedAt() == null ? null : view.decidedAt().toString(), view.decisionReason(),
                view.reservationStatus().name(), view.paymentStatus().name());
    }

    private static Long cents(Cents value) {
        return value == null ? null : value.amount();
    }
}
