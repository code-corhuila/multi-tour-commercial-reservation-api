package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SendProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;

// HU-COM-018 AC-18.3, AC-18.4, AC-18.9: the operator proposes a new departure for an episode; the seats of the party are held there until the customer answers.
public interface SendReschedulingProposal {
    ReschedulingEpisode send(SendProposalCommand command);
}
