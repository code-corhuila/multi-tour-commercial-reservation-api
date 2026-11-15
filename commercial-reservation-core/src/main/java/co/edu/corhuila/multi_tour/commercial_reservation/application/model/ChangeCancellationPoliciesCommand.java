package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicyValues;
import java.util.List;

// The author and the time are the actor and the clock; the reason is mandatory (HU-COM-013 DP-02d).
public record ChangeCancellationPoliciesCommand(PaymentActor actor, String reason, List<CancellationPolicyValues> policies) {
}
