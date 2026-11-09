package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceDetail;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

// CreditBalanceDetail of the contract: the credit with the history of its applications and refund requests. The reason of a request is not part of it (RNF-03).
public record CreditBalanceDetailResponse(UUID creditBalanceId, UUID sourceReservationId, UUID customerId, String origin, long recordedCents, long appliedCents,
        long inRefundCents, long availableCents, String recordedAt, UUID recordedBy, List<CreditApplicationResponse> applications,
        List<Request> refundRequests) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(UUID financialDecisionId, long amountCents, String requestedAt, String outcome) {}

    static CreditBalanceDetailResponse of(CreditBalanceDetail detail) {
        CreditBalanceResponse base = CreditBalanceResponse.of(detail.view());
        return new CreditBalanceDetailResponse(base.creditBalanceId(), base.sourceReservationId(), base.customerId(), base.origin(), base.recordedCents(),
                base.appliedCents(), base.inRefundCents(), base.availableCents(), base.recordedAt(), base.recordedBy(),
                detail.applications().stream().map(CreditApplicationResponse::of).toList(),
                detail.refundRequests().stream().map(d -> new Request(d.id(), d.baseAmount().amount(), d.recognizedAt().toString(),
                        d.outcome() == null ? null : d.outcome().name())).toList());
    }
}
