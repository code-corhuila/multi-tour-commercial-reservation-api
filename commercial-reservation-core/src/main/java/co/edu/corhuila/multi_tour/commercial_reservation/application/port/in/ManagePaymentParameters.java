package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangePaymentParametersCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import java.util.List;

// Reads are for every operating role; only the Tenant Administrator changes (AC-09.17). A change reaches new reservations only.
public interface ManagePaymentParameters {

    List<PaymentParameter> read(PaymentActor actor);

    List<PaymentParameter> change(ChangePaymentParametersCommand command);
}
