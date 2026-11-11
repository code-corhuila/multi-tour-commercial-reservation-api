package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationQuote.Reason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangePolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

// RN-03: the one reading of the terms, shared by the quote and the registration. No policy, a policy that does not allow, and a request
// later than the lead time before the departure start are three different refusals; the first that applies wins. Null means allowed.
final class ChangeTerms {

    private ChangeTerms() {
    }

    static Reason refusal(Optional<ChangePolicy> policy, Instant departureStart, Instant now) {
        if (policy.isEmpty()) return Reason.NO_POLICY;
        if (!policy.get().allowed()) return Reason.NOT_ALLOWED;
        boolean late = departureStart == null
                || Duration.between(now, departureStart).compareTo(Duration.ofMinutes(policy.get().leadMinutes())) < 0;
        return late ? Reason.TOO_LATE : null;
    }
}
