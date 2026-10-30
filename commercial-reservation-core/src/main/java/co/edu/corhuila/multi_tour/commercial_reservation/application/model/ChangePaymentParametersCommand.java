package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameterValues;
import java.util.List;

// The author and the time are the actor's and the clock's; the reason is mandatory (AC-09.17).
public record ChangePaymentParametersCommand(PaymentActor actor, String reason, List<PaymentParameterValues> parameters) {
}
