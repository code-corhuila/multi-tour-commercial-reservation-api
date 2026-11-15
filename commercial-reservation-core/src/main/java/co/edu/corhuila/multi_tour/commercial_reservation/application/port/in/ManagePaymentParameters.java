package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangePaymentParametersCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentParameterOption;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import java.util.List;

// Reads are for every operating role; only the Tenant Administrator changes (AC-09.17). A change reaches new reservations only.
public interface ManagePaymentParameters {

    List<PaymentParameter> read(PaymentActor actor);

    // The reduced projection that the Final Customer may read before the reservation exists (PDR 1.9, AC-RS.4); every role reads it.
    List<PaymentParameterOption> readOptions(PaymentActor actor);

    List<PaymentParameter> change(ChangePaymentParametersCommand command);
}
