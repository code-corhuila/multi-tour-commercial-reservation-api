package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadSensitiveCustomerData;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SensitiveDataAccessOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataAccessed;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public class ReadSensitiveCustomerDataUseCase implements ReadSensitiveCustomerData {
    private final CustomerRepository repository;
    private final SensitiveDataAccessOutbox outbox;
    private final CustomerTransaction transaction;
    private final Clock clock;

    public ReadSensitiveCustomerDataUseCase(CustomerRepository repository, SensitiveDataAccessOutbox outbox,
            CustomerTransaction transaction, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public Optional<HighRiskData> read(CustomerActor actor, UUID customerId, String reason) {
        // Permission first, so a denied actor learns nothing about which customers exist (RN-07).
        CustomerAuthorization.requireSensitiveRead(actor);
        // The tenant is the actor's: a customer of another tenant looks missing (RN-12).
        var customer = repository.findByTenantAndId(actor.tenantId(), customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        // The audit event commits with the read and before the response; if it fails nothing is returned (D-08).
        return transaction.inTransaction(() -> {
            outbox.append(new SensitiveDataAccessed(actor.tenantId(), customer.customerId(), actor.actorId(),
                    normalize(reason), clock.instant()));
            return Optional.ofNullable(customer.highRiskData());
        });
    }

    // The reason is optional (DP-11 default): blank reads as none.
    private static String normalize(String reason) {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }
}
