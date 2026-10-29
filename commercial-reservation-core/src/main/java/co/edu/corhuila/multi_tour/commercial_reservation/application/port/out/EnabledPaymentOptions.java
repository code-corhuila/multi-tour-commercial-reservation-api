package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.util.UUID;

// RN-14: the payment type and medium must be enabled for the tenant and, when configured, for the service. Where that
// configuration lives is DP-05 (open), so the adapter is a double until it closes.
public interface EnabledPaymentOptions {

    boolean isEnabled(UUID tenantId, UUID serviceId, PaymentType type, PaymentMethod method);
}
