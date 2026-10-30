package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSourcePage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Instant;
import java.util.UUID;

// Read side of the review: attempts of one tenant by validation status, ordered by presentation time, optionally only those
// presented at or before a cutoff (the overdue ones). Page from 1. Never carries the file.
public interface PaymentProofReader {

    ProofSourcePage find(UUID tenantId, ValidationStatus status, Instant registeredAtOrBefore, int page, int limit);

    long countAtOrBefore(UUID tenantId, ValidationStatus status, Instant registeredAtOrBefore);
}
