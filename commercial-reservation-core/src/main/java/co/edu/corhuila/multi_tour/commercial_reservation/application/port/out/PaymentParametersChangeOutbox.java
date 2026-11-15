package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParametersChange;

// HU-COM-009 DP-04 (Project Lead, 2026-11-12): the outbox of the parameter change, written in the transaction of the change (norm 5.3.11). The event is PaymentParametersChanged, one per
// changed row: the tenant, the author, the payment type, the service when the row has one, and the instant; never a value of a parameter or the reason. The audit domain stores it.
public interface PaymentParametersChangeOutbox {

    void append(PaymentParametersChange change);
}
