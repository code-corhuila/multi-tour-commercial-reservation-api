package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.util.Optional;
import java.util.UUID;

// HU-COM-018 plan D-02: the episode with its proposals, always scoped to the tenant. Saving inserts a new episode or writes the forward move of an existing one
// (the guards of the db refuse anything else). The adapter is JDBC, in a later pull request.
public interface ReschedulingRepository {

    Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID originalSalidaId);

    Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId);

    void save(ReschedulingEpisode episode);

    // HU-COM-018 AC-18.12: the open episodes whose pending proposal is for the given departure, to void them when it is cancelled.
    default java.util.List<ReschedulingEpisode> findPendingOn(UUID tenantId, UUID proposedSalidaId) {
        throw new UnsupportedOperationException("the lookup of the proposals of a departure needs the adapter override");
    }
}
