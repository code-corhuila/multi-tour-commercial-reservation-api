package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// FinancialDecision of the contract (AC-16.14), never editable. decisionDueAt feeds the counter; frozen, it is the freeze instant plus the minutes left.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FinancialDecisionResponse(UUID financialDecisionId, UUID reservationId, String kind, long baseAmountCents, Long penaltyCents, UUID creditBalanceId,
        String recognizedAt, String decisionDueAt, boolean frozen, String outcome, String decidedAt, UUID decidedBy, Boolean decidedBySystem,
        String justification) {

    static FinancialDecisionResponse of(FinancialDecision d) {
        Instant due = d.isFrozen() ? d.frozenAt().plus(Duration.ofMinutes(d.remainingAtFreezeMinutes())) : d.dueAt();
        return new FinancialDecisionResponse(d.id(), d.reservationId(), d.kind().name(), d.baseAmount().amount(),
                d.penalty() == null ? null : d.penalty().amount(), d.creditBalanceId(), d.recognizedAt().toString(), due.toString(), d.isFrozen(),
                d.outcome() == null ? null : d.outcome().name(), d.decidedAt() == null ? null : d.decidedAt().toString(), d.decidedBy(),
                d.outcome() == null ? null : d.decidedBySystem(), d.rejectionJustification());
    }
}
