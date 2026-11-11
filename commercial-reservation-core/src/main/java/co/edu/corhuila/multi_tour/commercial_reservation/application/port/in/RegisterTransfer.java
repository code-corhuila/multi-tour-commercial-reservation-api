package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterTransferCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisteredPayment;

// Registers a total or partial transfer with its proof, in validation. It holds the seats on the first payment and stores
// nothing on a refusal (AC-09.1, AC-09.6).
public interface RegisterTransfer {

    RegisteredPayment register(RegisterTransferCommand command);
}
