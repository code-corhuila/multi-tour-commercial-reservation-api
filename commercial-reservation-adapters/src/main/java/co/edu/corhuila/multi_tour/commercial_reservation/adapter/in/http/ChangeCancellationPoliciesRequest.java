package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicyValues;
import java.util.List;
import java.util.UUID;

// The author and the time come from the token and the clock; any other field is refused by the strict mapper. The rows are validated by the use case, after the role.
public record ChangeCancellationPoliciesRequest(String reason, List<Row> policies) {

    public record Row(UUID serviceId, Integer penaltyBasisPoints) {
    }

    List<CancellationPolicyValues> values() {
        return policies == null ? null : policies.stream().map(row -> new CancellationPolicyValues(row.serviceId(), row.penaltyBasisPoints())).toList();
    }
}
