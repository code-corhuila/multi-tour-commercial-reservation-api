package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;

// The outbox of the payment family, written in the transaction of the change (norm 5.3.11). Events carry no file.
public interface PaymentEventOutbox {

    void append(PaymentEvent event);
}
