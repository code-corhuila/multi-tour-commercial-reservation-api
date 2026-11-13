package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

// A policy row with its last change only. An empty serviceId is not written: the row is the policy of the whole tenant.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancellationPolicyResponse(UUID serviceId, int penaltyBasisPoints, UUID changedBy, String changeReason, String changedAt) {

    static CancellationPolicyResponse of(CancellationPolicy row) {
        return new CancellationPolicyResponse(row.serviceId(), row.penaltyBasisPoints(), row.changedBy(), row.changeReason(), row.changedAt().toString());
    }
}
