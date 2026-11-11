package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// Never carries the file, its name, bank data or personal data (RN-15); the validation status is null for cash.
public record PaymentEvidenceRegistered(UUID tenantId, UUID reservationId, UUID attemptId, PaymentMethod modality,
        Cents registeredAmount, ValidationStatus validationStatus, Instant occurredAt) implements PaymentEvent {
}
