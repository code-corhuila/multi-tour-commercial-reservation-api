package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// A parameter row with its last change only: author, reason and time (DP-04, DD-5). The reason is mandatory.
public record PaymentParameter(PaymentParameterValues values, UUID changedBy, String changeReason, Instant changedAt) {

    public PaymentParameter {
        if (values == null) throw new DomainException("parameters", "the parameter values are required");
        if (changedBy == null) throw new DomainException("changedBy", "the author of the change is required");
        if (changedAt == null) throw new DomainException("changedAt", "the moment of the change is required");
        if (changeReason == null || changeReason.isBlank()) throw new DomainException("reason", "a reason is required");
        changeReason = changeReason.strip();
        if (changeReason.length() > 500) throw new DomainException("reason", "the reason cannot exceed 500 characters");
    }
}
