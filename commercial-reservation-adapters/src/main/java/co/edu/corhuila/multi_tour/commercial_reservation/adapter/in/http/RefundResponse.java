package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

// RefundAuthorization of the contract (AC-16.14): the whole record, no field editable. The reason is shown here and nowhere else (RNF-03). The overdue
// flag is the mark, never a state (AC-16.11). Money is cents; absent facts are absent.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefundResponse(UUID refundAuthorizationId, UUID reservationId, UUID financialDecisionId, String origin, long potentialValueCents,
        long authorizedAmountCents, String reason, String authorizationMode, UUID authorizedBy, String authorizedAt, String executionDueAt, boolean overdue,
        String overdueMarkedAt, String executionState, String outflowMethod, UUID executedBy, String executionStartedAt, UUID cashMovementId,
        String executedAt) {

    static RefundResponse of(RefundAuthorization r) {
        return new RefundResponse(r.id(), r.reservationId(), r.financialDecisionId(), r.origin().name(), r.potentialRefundValue().amount(),
                r.authorizedAmount().amount(), r.reason(), r.mode().name(), r.authorizedBy(), text(r.authorizedAt()), text(r.executionDueAt()),
                r.overdueMarkedAt() != null, text(r.overdueMarkedAt()), r.state().name(), r.outflowMethod() == null ? null : r.outflowMethod().name(),
                r.executedBy(), text(r.executingAt()), r.cashMovementId(), text(r.executedAt()));
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
