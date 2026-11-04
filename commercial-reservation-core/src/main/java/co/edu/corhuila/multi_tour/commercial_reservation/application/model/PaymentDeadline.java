package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EffectiveDeadline;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// The open deadline of a reservation: which one applies, when it ends, its single extension and its freeze (HU-COM-011, RN-03, RN-09).
public record PaymentDeadline(DeadlineKind kind, Instant dueAt, Integer extensionHours, Instant extensionGrantedAt,
        UUID extensionGrantedBy, Instant frozenAt, Integer remainingAtFreezeMinutes) {

    public PaymentDeadline(DeadlineKind kind, Instant dueAt) {
        this(kind, dueAt, null, null, null, null, null);
    }

    public boolean frozen() {
        return frozenAt != null;
    }

    public Instant effective(Instant departureStart) {
        return EffectiveDeadline.of(dueAt, extensionHours == null ? 0 : extensionHours, departureStart);
    }

    // The one extension: the initial deadline, still open and not frozen, within 1 hour and the maximum, never past the departure start.
    public PaymentDeadline extended(int hours, int maximum, UUID grantedBy, Instant now, Instant departureStart) {
        if (kind != DeadlineKind.INITIAL_PAYMENT) throw new BusinessRuleViolation("only the initial payment deadline can be extended");
        if (extensionHours != null) throw new BusinessRuleViolation("the deadline was already extended once");
        if (frozen()) throw new BusinessRuleViolation("a frozen deadline cannot be extended");
        if (!now.isBefore(effective(departureStart))) throw new BusinessRuleViolation("the deadline has already expired");
        if (hours > maximum) throw new BusinessRuleViolation("the extension is above the maximum of the payment condition");
        if (departureStart != null && dueAt.plus(Duration.ofHours(hours)).isAfter(departureStart))
            throw new BusinessRuleViolation("the extension would pass the departure start");
        return new PaymentDeadline(kind, dueAt, hours, now, grantedBy, frozenAt, remainingAtFreezeMinutes);
    }
}
