package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueRescheduling;
import java.time.Instant;
import java.util.List;

// HU-COM-018 AC-18.10, AC-18.11; worker job 5: the open episodes of every tenant that have a clock run out at the instant: the 24 hours to propose with no proposal pending, the response deadline of a
// pending proposal, or the 96-hour limit. Read only; the service decides again when the expiry is asked (it answers that nothing is due if it changed meanwhile).
public interface ReschedulingQueries {

    List<DueRescheduling> due(Instant now, int limit);
}
