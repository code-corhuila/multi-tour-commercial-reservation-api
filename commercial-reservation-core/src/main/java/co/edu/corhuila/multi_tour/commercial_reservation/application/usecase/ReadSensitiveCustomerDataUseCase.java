package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadSensitiveCustomerData;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SensitiveDataAccessOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public class ReadSensitiveCustomerDataUseCase implements ReadSensitiveCustomerData {
    public ReadSensitiveCustomerDataUseCase(CustomerRepository repository, SensitiveDataAccessOutbox outbox,
            CustomerTransaction transaction, Clock clock) {
    }

    @Override
    public Optional<HighRiskData> read(CustomerActor actor, UUID customerId, String reason) {
        return Optional.empty();
    }
}
