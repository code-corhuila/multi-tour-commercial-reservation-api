package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AnswerProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;

// HU-COM-018 AC-18.6: the customer answers their own proposal, or staff records an answer received by an outside channel; accepting takes effect at once.
public interface AnswerReschedulingProposal {
    ReschedulingEpisode accept(AnswerProposalCommand command);
}
