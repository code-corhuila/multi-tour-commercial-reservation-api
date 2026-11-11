package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OpenReschedulingCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;

// HU-COM-018 AC-18.8: the saga of the cancelled departure opens the episode of one reservation; idempotent by reservation and incident.
public interface OpenRescheduling {
    ReschedulingEpisode open(OpenReschedulingCommand command);
}
