package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;
import java.util.UUID;

// The fields of PublicTour plus the departures that can be booked now (plan D-02, AC-06.3). An empty list says
// the tour has none; maxTravelers is the configured capacity of the tour, never a departure figure (DP-06).
public record PublicTourDetail(UUID serviceId, String commercialName, boolean transportIncluded, long baseValueCents,
        long finalValueCents, Integer discountPercentageBp, int maxTravelers, List<PublicDeparture> departures) {
    public PublicTourDetail {
        departures = List.copyOf(departures);
    }
}
