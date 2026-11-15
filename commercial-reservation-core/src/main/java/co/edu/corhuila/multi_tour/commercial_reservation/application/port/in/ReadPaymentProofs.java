package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofView;
import java.util.List;
import java.util.UUID;

// One proof and the history of a reservation, rejected attempts included (AC-10.14, AC-09.16).
public interface ReadPaymentProofs {

    ProofView get(PaymentActor actor, UUID attemptId);

    List<ProofView> history(PaymentActor actor, UUID reservationId);
}
