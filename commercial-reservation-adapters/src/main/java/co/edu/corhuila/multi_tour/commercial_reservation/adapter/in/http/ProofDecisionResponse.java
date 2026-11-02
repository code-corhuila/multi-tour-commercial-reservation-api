package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;

// The decided proof and the reservation recalculated by it: the validated value is the final value less what is still pending.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProofDecisionResponse(@JsonUnwrapped ProofResponse proof, long validatedValueCents) {

    static ProofDecisionResponse of(DecidedProof decided, String customerName, Instant now) {
        var reservation = decided.reservation();
        return new ProofDecisionResponse(ProofResponse.of(ProofView.of(reservation, decided.attempt(), now), customerName),
                reservation.finalValue().amount() - reservation.pendingBalance().amount());
    }
}
