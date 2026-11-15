package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;

// The only two fields a caller sends: the user, tenant, state or any other field is refused by the strict mapper (AC-09.9).
public record RegisterPaymentRequest(String paymentMethod, Long amountCents) {

    PaymentMethod method() {
        if (paymentMethod == null) throw new DomainException("paymentMethod", "paymentMethod is required");
        try {
            return PaymentMethod.valueOf(paymentMethod);
        } catch (IllegalArgumentException invalid) {
            throw new DomainException("paymentMethod", "paymentMethod must be TRANSFER or CASH");
        }
    }
}
