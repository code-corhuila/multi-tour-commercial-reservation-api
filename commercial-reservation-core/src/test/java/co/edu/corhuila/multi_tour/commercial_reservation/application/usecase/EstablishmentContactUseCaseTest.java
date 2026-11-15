package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor.Role.TENANT_ADMINISTRATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAssociatedEstablishment.Command;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentContact;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-002 AC-02.12: the contact travels with the registration and is stored with the association.
class EstablishmentContactUseCaseTest {
    private final EstablishmentActor admin = new EstablishmentActor(new UUID(0, 1), new UUID(0, 2), TENANT_ADMINISTRATOR);
    private final InMemoryAssociatedEstablishmentRepository repository = new InMemoryAssociatedEstablishmentRepository();
    private final InMemoryEstablishmentKeys keys = new InMemoryEstablishmentKeys(repository);
    private final RegisterAssociatedEstablishmentUseCase register = new RegisterAssociatedEstablishmentUseCase(
            repository, keys, UUID::randomUUID, Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC));

    @Test void theContactIsStoredAndReadBack() {
        var created = register.register(admin, "register-key", command("info@hotel.example", "+57 8 8700000", "Calle 1")).association();
        var read = new GetAssociatedEstablishmentUseCase(repository).get(admin, created.associationId());
        assertThat(read.contact()).isEqualTo(new EstablishmentContact("info@hotel.example", "+57 8 8700000", "Calle 1"));
    }

    @Test void aPhoneAloneRegistersAndTheOtherFieldsStayAbsent() {
        var created = register.register(admin, "register-key", command(null, "+57 8 8700000", "")).association();
        assertThat(created.contact()).isEqualTo(new EstablishmentContact(null, "+57 8 8700000", null));
    }

    @Test void noEmailAndNoPhoneIsRefusedAndNothingIsWritten() {
        assertThatThrownBy(() -> register.register(admin, "register-key", command(" ", null, "Calle 1")))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.field()).isEqualTo("contactEmail"));
        assertThat(repository.rows).isEmpty();
        assertThat(keys.entries).isEmpty();
        assertThat(repository.outbox).isEmpty();
    }

    private Command command(String email, String phone, String address) {
        return new Command("HOTEL", "Hotel example", "Promotional information", "opaque-reference", email, phone, address);
    }
}
