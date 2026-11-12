package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReschedulingView;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import java.util.Optional;
import java.util.UUID;

// HU-COM-018 AC-18.16, AC-18.18: the reads of the episodes. The tenant always leads; a non-null customerId is a condition of the query (the customer of the reservation), so an episode
// of another customer looks missing. Narrowed by reservation, original departure and state; ordered by the instant the departure was cancelled, newest first.
public interface ReschedulingReads {
    RefundPage<ReschedulingView> page(UUID tenantId, UUID customerId, UUID reservationId, UUID departureId, EpisodeState state, int page, int limit);

    Optional<ReschedulingView> find(UUID tenantId, UUID customerId, UUID episodeId);
}
