package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// HU-COM-019 RN-12, AC-19.16: the record of one retention deletion, one per reservation. It keeps identifiers, the anchor and its basis, the instant and two counts, and
// never a name, a document, a contact or a medical value.
public record SensitiveDataDeletion(UUID reservationId, UUID tenantId, UUID customerId, Instant anchorAt, AnchorBasis basis, Instant deletedAt, boolean customerDataDeleted,
        int companionRowsDeleted) {
}
