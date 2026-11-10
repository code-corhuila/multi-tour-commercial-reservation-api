package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EffectiveDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// The open deadline of a reservation: which one applies, when it ends, its single extension and its freeze with the cause (HU-COM-011, RN-03, RN-09; HU-COM-020, RN-02, RN-03).
public record PaymentDeadline(DeadlineKind kind, Instant dueAt, Integer extensionHours, Instant extensionGrantedAt,
        UUID extensionGrantedBy, Instant frozenAt, Integer remainingAtFreezeMinutes, FreezeCause freezeCause) {

    public PaymentDeadline(DeadlineKind kind, Instant dueAt, Integer extensionHours, Instant extensionGrantedAt,
            UUID extensionGrantedBy, Instant frozenAt, Integer remainingAtFreezeMinutes) {
        this(kind, dueAt, extensionHours, extensionGrantedAt, extensionGrantedBy, frozenAt, remainingAtFreezeMinutes, null);
    }

    public PaymentDeadline(DeadlineKind kind, Instant dueAt) {
        this(kind, dueAt, null, null, null, null, null, null);
    }

    public boolean frozen() {
        return frozenAt != null;
    }

    public Instant effective(Instant departureStart) {
        return EffectiveDeadline.of(dueAt, extensionHours == null ? 0 : extensionHours, departureStart);
    }

    // The instant to show and to judge (RN-02, RN-17): the effective deadline; a frozen one has no running instant.
    public Instant inForce(Instant departureStart) {
        return frozen() ? null : effective(departureStart);
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
        return new PaymentDeadline(kind, dueAt, hours, now, grantedBy, frozenAt, remainingAtFreezeMinutes, freezeCause);
    }

    // HU-COM-020 RN-02, AC-20.2: frozen at the instant of the event with the time it had left (zero if already reached) and the cause; the extension stays recorded. A frozen deadline is not frozen again.
    public PaymentDeadline frozen(Instant at, Instant departureStart, FreezeCause cause) {
        if (frozen()) return this;
        long remaining = Math.max(0, Duration.between(at, effective(departureStart)).toMinutes());
        return new PaymentDeadline(kind, dueAt, extensionHours, extensionGrantedAt, extensionGrantedBy, at, (int) remaining, cause);
    }

    // HU-COM-020 RN-03, AC-20.3, AC-20.4, AC-20.18: resumes only a deadline frozen by the given cause: the effective deadline becomes the instant of the event plus the remaining time, never past the
    // start of the departure. A deadline whose departure was cancelled is not resumed: it stays frozen and its cause becomes the departure. Any other deadline is untouched.
    public PaymentDeadline resumed(Instant at, Instant departureStart, FreezeCause cause, boolean departureCancelled) {
        if (!frozen() || freezeCause != cause) return this;
        if (departureCancelled)
            return new PaymentDeadline(kind, dueAt, extensionHours, extensionGrantedAt, extensionGrantedBy, frozenAt, remainingAtFreezeMinutes, FreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED);
        Instant target = at.plus(Duration.ofMinutes(remainingAtFreezeMinutes));
        if (departureStart != null && departureStart.isBefore(target)) target = departureStart;
        return new PaymentDeadline(kind, target.minus(Duration.ofHours(extensionHours == null ? 0 : extensionHours)), extensionHours, extensionGrantedAt, extensionGrantedBy, null, null, null);
    }
}
