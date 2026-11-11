package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RetentionFacts;
import java.util.Optional;
import java.util.UUID;

// HU-COM-019 plan D-03, G-B: the departure facts come from the local mirror, never from another domain (ADR-004 D4). A reservation of another tenant or a missing one is
// empty; a departure without an end on the mirror reads as unknown facts, so the reservation simply has no anchor.
public interface RetentionFactsReader {

    Optional<RetentionFacts> factsOf(UUID tenantId, UUID reservationId);
}
