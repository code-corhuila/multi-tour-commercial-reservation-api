package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import java.util.List;
import java.util.UUID;

// payment_condition: a row per payment type and optional service, replaced by the last change. No delete.
public interface PaymentParameterRepository {

    List<PaymentParameter> findByTenant(UUID tenantId);

    void save(UUID tenantId, PaymentParameter parameter);
}
