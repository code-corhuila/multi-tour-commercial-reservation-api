package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.util.UUID;

// HU-COM-018 AC-18.16: an episode as the reads show it, with the customer of its reservation (the owner a Final Customer is compared with).
public record ReschedulingView(ReschedulingEpisode episode, UUID customerId) {
}
