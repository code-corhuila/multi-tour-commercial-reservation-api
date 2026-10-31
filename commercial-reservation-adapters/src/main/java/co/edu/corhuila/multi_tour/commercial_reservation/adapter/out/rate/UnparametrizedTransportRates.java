package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.rate;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TransportRateReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.Optional;
import java.util.UUID;

// HU-COM-007 DP-03 (OPEN): the per-person transport fare of a tour has no column or table in the -db yet, and the catalog holds
// only transport_required. Until the data owner closes it no tour has a parametrized fare: a tour that does not require
// transport is quoted and created, and one that does is refused by the use case (RN-15) instead of being charged a guessed rate.
public final class UnparametrizedTransportRates implements TransportRateReader {
    @Override
    public Optional<Cents> perPersonCents(UUID tenantId, UUID tourId, UUID departureId) {
        return Optional.empty();
    }
}
