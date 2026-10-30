package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterCashCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisteredPayment;

// Registers cash received by the office: it counts as money at once, needs an open cash session and is never a proof to
// review (DP-01, AC-09.2, AC-09.5).
public interface RegisterCashPayment {

    RegisteredPayment register(RegisterCashCommand command);
}
