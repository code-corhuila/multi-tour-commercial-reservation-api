package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor.Role.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAssociatedEstablishment.Command;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

// HU-COM-002 AC-02.13 to AC-02.16: every inactivation and reactivation needs a reason and leaves a trace.
class EstablishmentChangeLogTest {
    private static final UUID TENANT = new UUID(0, 1), OTHER = new UUID(0, 2), ACTOR = new UUID(0, 3);
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final EstablishmentActor admin = new EstablishmentActor(TENANT, ACTOR, TENANT_ADMINISTRATOR);
    private final InMemoryAssociatedEstablishmentRepository repository = new InMemoryAssociatedEstablishmentRepository();
    private final InMemoryEstablishmentKeys keys = new InMemoryEstablishmentKeys(repository);
    private final RegisterAssociatedEstablishmentUseCase register = new RegisterAssociatedEstablishmentUseCase(repository, keys, UUID::randomUUID, clock);
    private final InactivateAssociatedEstablishmentUseCase inactivate = new InactivateAssociatedEstablishmentUseCase(repository, clock);
    private final ReactivateAssociatedEstablishmentUseCase reactivate = new ReactivateAssociatedEstablishmentUseCase(repository, clock);
    private final ListEstablishmentChangesUseCase list = new ListEstablishmentChangesUseCase(repository);

    @ParameterizedTest @NullSource @ValueSource(strings = {"", "   "})
    void aMissingOrBlankReasonIsRefusedAndNothingChanges(String reason) {
        var id = registered("Hotel one").associationId();
        assertThatThrownBy(() -> inactivate.inactivate(admin, id, reason)).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.field()).isEqualTo("reason"));
        assertThat(repository.rows.get(id).associationStatus()).isEqualTo(AssociationStatus.ACTIVE);
        assertThat(repository.changes).hasSize(1);
    }

    @Test void aReasonOver500CharactersIsRefused() {
        var id = registered("Hotel one").associationId();
        assertThatThrownBy(() -> inactivate.inactivate(admin, id, "x".repeat(501))).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.field()).isEqualTo("reason"));
        assertThat(repository.rows.get(id).associationStatus()).isEqualTo(AssociationStatus.ACTIVE);
    }

    @Test void theRegistrationAndEachTransitionLeaveOneEntryWithTheirReasonAndAuthor() {
        var id = registered("Hotel one").associationId();
        inactivate.inactivate(admin, id, "Closed for works");
        var later = new EstablishmentActor(TENANT, new UUID(0, 4), TENANT_ADMINISTRATOR);
        new ReactivateAssociatedEstablishmentUseCase(repository, Clock.offset(clock, Duration.ofSeconds(60)))
                .reactivate(later, id, "Works finished");
        assertThat(repository.changes).extracting(EstablishmentChange::eventType, EstablishmentChange::reason, EstablishmentChange::actorId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(EstablishmentChangeType.REGISTERED, null, ACTOR),
                        org.assertj.core.groups.Tuple.tuple(EstablishmentChangeType.INACTIVATED, "Closed for works", ACTOR),
                        org.assertj.core.groups.Tuple.tuple(EstablishmentChangeType.REACTIVATED, "Works finished", later.actorId()));
        assertThat(repository.changes.get(2).occurredAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(repository.changes.get(1).commercialName()).isEqualTo("Hotel one");
    }

    @Test void repeatingATransitionRecordsNothingNew() {
        var id = registered("Hotel one").associationId();
        inactivate.inactivate(admin, id, "Closed for works");
        inactivate.inactivate(admin, id, "Closed again");
        assertThat(repository.changes).hasSize(2);
    }

    @Test void theLogIsNewestFirstFilteredByTypeAndOfTheTenantOnly() {
        var hotel = registered("Hotel one").associationId();
        register.register(admin, "restaurant-key", new Command("RESTAURANT", "Restaurant one", "Info", "ref-2", "info@hotel.example", "+57 8 8700000", "Calle 1 No. 2-3"));
        var later = new InactivateAssociatedEstablishmentUseCase(repository, Clock.offset(clock, Duration.ofSeconds(5)));
        later.inactivate(admin, hotel, "Closed for works");
        var foreign = new EstablishmentActor(OTHER, ACTOR, TENANT_ADMINISTRATOR);
        register.register(foreign, "foreign-key", new Command("HOTEL", "Foreign", "Info", "ref-3", "info@hotel.example", "+57 8 8700000", "Calle 1 No. 2-3"));
        var all = list.list(admin, Optional.empty(), 1, 20);
        assertThat(all.total()).isEqualTo(3);
        assertThat(all.items().get(0).eventType()).isEqualTo(EstablishmentChangeType.INACTIVATED);
        assertThat(all.items()).extracting(EstablishmentChange::commercialName).doesNotContain("Foreign");
        var hotels = list.list(admin, Optional.of(EstablishmentType.HOTEL), 1, 20);
        assertThat(hotels.total()).isEqualTo(2);
        assertThat(list.list(admin, Optional.empty(), 2, 2).items()).hasSize(1);
    }

    @ParameterizedTest @EnumSource(value = EstablishmentActor.Role.class, names = {"MANAGER", "ANALYST", "TENANT_ADMINISTRATOR"})
    void administratorManagerAndAnalystReadTheLog(EstablishmentActor.Role role) {
        registered("Hotel one");
        assertThat(list.list(new EstablishmentActor(TENANT, ACTOR, role), Optional.empty(), 1, 20).total()).isEqualTo(1);
    }

    @ParameterizedTest @EnumSource(value = EstablishmentActor.Role.class, names = {"ACCOUNTANT", "OPERATIONAL_COLLABORATOR"})
    void accountantAndCollaboratorAreDenied(EstablishmentActor.Role role) {
        assertThatThrownBy(() -> list.list(new EstablishmentActor(TENANT, ACTOR, role), Optional.empty(), 1, 20))
                .isInstanceOf(EstablishmentAccessDeniedException.class);
    }

    @Test void invalidPagesAreRefusedNamingTheField() {
        assertThatThrownBy(() -> list.list(admin, Optional.empty(), 0, 20)).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.field()).isEqualTo("page"));
        assertThatThrownBy(() -> list.list(admin, Optional.empty(), 1, 101)).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.field()).isEqualTo("limit"));
    }

    private AssociatedEstablishment registered(String name) {
        return register.register(admin, "key-" + name, new Command("HOTEL", name, "Info", "ref-" + name, "info@hotel.example", "+57 8 8700000", "Calle 1 No. 2-3")).association();
    }
}
