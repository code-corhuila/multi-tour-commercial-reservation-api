package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;

// HU-COM-016 D-09, DP-10: RefundAuthorized in the transaction of the authorization, with identifiers, amount, origin and mode only; the reason is never evented.
public interface RefundAuthorizedOutbox {

    void append(RefundAuthorization refund);
}
