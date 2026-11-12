package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// HU-COM-019 RN-02, DD-2: the definitive closing of a cancelled reservation, derived from the settlement of its financial decisions (HU-COM-016, HU-COM-017). When the inactivation of the
// tenant froze a decision period the closing is blocked, and the year counts from the cancellation instead (AC-19.5).
public record Closing(Instant at, boolean blockedByInactivation) {

    // PROPOSED rule (owner Project Lead): the reservation is closed when every one of its decisions is settled, at the latest settlement; an open decision frozen by the
    // inactivation blocks the closing; any other open decision leaves it open; no decision at all means nothing to close (the anchor is the cancellation).
    public static Optional<Closing> of(List<DecisionSettlement> decisions) {
        return Optional.empty();
    }
}
