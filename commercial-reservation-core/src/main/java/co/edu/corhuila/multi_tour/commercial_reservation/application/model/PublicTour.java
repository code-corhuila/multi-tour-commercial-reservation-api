package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// The only fields of a tour a browser may see (plan D-02, RN-06). No cost, no risk flag, no transport detail.
// maxTravelers is the configured capacity (DP-06); discountPercentageBp is null when no rule applies.
public record PublicTour(UUID serviceId, String commercialName, boolean transportIncluded, long baseValueCents,
        long finalValueCents, Integer discountPercentageBp, int maxTravelers) {
}
