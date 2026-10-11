package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.*;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory.InMemoryEstablishments;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterAssociatedEstablishmentUseCase;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EstablishmentMemoryTransactionTest {
    @Test void aFailedImageWriteRollsBackTheAssociationKeyAndImage() {
        var store = new InMemoryEstablishments();
        var actor = new EstablishmentActor(UUID.randomUUID(), UUID.randomUUID(), EstablishmentActor.Role.TENANT_ADMINISTRATOR);
        var useCase = new RegisterAssociatedEstablishmentUseCase(store, store, UUID::randomUUID,
                Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC));
        var command = new RegisterAssociatedEstablishment.Command("HOTEL", "Hotel", "Contact", "opaque-image-reference");
        assertThatThrownBy(() -> store.register(null, "image/png", () -> useCase.register(actor, "rollback-key", command)))
                .isInstanceOf(NullPointerException.class);
        assertThat(store.listByTenant(actor.tenantId(), java.util.Optional.empty(), java.util.Optional.empty(), 1, 20).total()).isZero();
        assertThatThrownBy(() -> store.read(actor.tenantId(), command.imageReference()))
                .isInstanceOf(AssociatedEstablishmentNotFoundException.class);
        var retry = store.register(EstablishmentHttpTest.PNG, "image/png", () -> useCase.register(actor, "rollback-key", command));
        assertThat(retry.created()).isTrue();
        assertThat(store.read(actor.tenantId(), command.imageReference()).bytes()).isEqualTo(EstablishmentHttpTest.PNG);
    }
}
