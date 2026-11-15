package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.util.Optional;
import java.util.UUID;

public interface ReadSensitiveCustomerData {
    // reason is optional (DP-11 default); empty when the customer has no high-risk data on file.
    Optional<HighRiskData> read(CustomerActor actor, UUID customerId, String reason);
}
