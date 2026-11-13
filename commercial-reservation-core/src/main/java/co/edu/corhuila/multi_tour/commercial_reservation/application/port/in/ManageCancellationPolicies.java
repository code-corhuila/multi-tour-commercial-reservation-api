package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangeCancellationPoliciesCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicy;
import java.util.List;

// HU-COM-013 DP-02: the staff roles read the penalty policies; only the Tenant Administrator changes them. A change is read at the instant of a cancellation and never
// rewrites one already registered.
public interface ManageCancellationPolicies {

    List<CancellationPolicy> read(PaymentActor actor);

    List<CancellationPolicy> change(ChangeCancellationPoliciesCommand command);
}
