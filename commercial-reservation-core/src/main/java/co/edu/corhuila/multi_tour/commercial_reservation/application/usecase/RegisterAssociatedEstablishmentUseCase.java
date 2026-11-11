package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentRegistration;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.time.Clock;

public final class RegisterAssociatedEstablishmentUseCase implements RegisterAssociatedEstablishment {
    private final AssociatedEstablishmentRepository repository;
    private final AssociatedEstablishmentKeys keys;
    private final IdGenerator ids;
    private final Clock clock;

    public RegisterAssociatedEstablishmentUseCase(AssociatedEstablishmentRepository repository,
            AssociatedEstablishmentKeys keys, IdGenerator ids, Clock clock) {
        this.repository = repository; this.keys = keys; this.ids = ids; this.clock = clock;
    }

    @Override public EstablishmentRegistration register(EstablishmentActor actor, String key, Command command) {
        EstablishmentAuthorization.require(actor, true);
        if (key == null || key.isBlank() || key.length() < 8 || key.length() > 128)
            throw new DomainException("Idempotency-Key", "Idempotency-Key must have 8 to 128 characters");
        return keys.register(actor.tenantId(), key, () -> {
            var item = AssociatedEstablishment.register(ids.newId(), actor.tenantId(),
                    EstablishmentType.parse(command.establishmentType()), command.commercialName(),
                    command.commercialInformation(), command.imageReference(), actor.actorId(), clock.instant());
            repository.save(item);
            return item;
        }, id -> EstablishmentLookup.require(repository, actor.tenantId(), id));
    }
}
