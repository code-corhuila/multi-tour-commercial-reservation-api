package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor.Role.*;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.*;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.*;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class AssociatedEstablishmentUseCasesTest {
    private static final UUID TENANT = new UUID(0, 101), OTHER = new UUID(0, 102), ACTOR = new UUID(0, 103);
    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final EstablishmentActor admin = actor(TENANT_ADMINISTRATOR);
    private final InMemoryAssociatedEstablishmentRepository repository = new InMemoryAssociatedEstablishmentRepository();
    private final InMemoryEstablishmentKeys keys = new InMemoryEstablishmentKeys(repository);
    private final RegisterAssociatedEstablishmentUseCase register =
            new RegisterAssociatedEstablishmentUseCase(repository, keys, UUID::randomUUID, clock);
    private final GetAssociatedEstablishmentUseCase get = new GetAssociatedEstablishmentUseCase(repository);
    private final ListAssociatedEstablishmentsUseCase list = new ListAssociatedEstablishmentsUseCase(repository);
    private final InactivateAssociatedEstablishmentUseCase inactivate = new InactivateAssociatedEstablishmentUseCase(repository, clock);
    private final ReactivateAssociatedEstablishmentUseCase reactivate = new ReactivateAssociatedEstablishmentUseCase(repository, clock);

    @Test void everyInputPortHasExactlyOneMethod() {
        for (Class<?> port : List.of(RegisterAssociatedEstablishment.class, GetAssociatedEstablishment.class,
                ListAssociatedEstablishments.class, InactivateAssociatedEstablishment.class, ReactivateAssociatedEstablishment.class)) {
            assertThat(port.isInterface()).isTrue();
            assertThat(port.getDeclaredMethods()).hasSize(1);
        }
    }

    @Test void administratorRegistersAndTransitionsWithThreePendingEvents() {
        var result = register.register(admin, "register-key", command());
        var item = result.association();
        assertThat(result.created()).isTrue();
        assertThat(item.pullEvents()).isEmpty();
        assertThat(get.get(admin, item.associationId()).commercialInformation()).isEqualTo("Contact details");
        inactivate.inactivate(admin, item.associationId());
        var laterActor = new EstablishmentActor(TENANT, new UUID(0, 104), TENANT_ADMINISTRATOR);
        var later = new ReactivateAssociatedEstablishmentUseCase(repository, Clock.offset(clock, Duration.ofSeconds(60)));
        later.reactivate(laterActor, item.associationId());
        assertThat(repository.outbox).containsExactly(
                new EstablishmentAssociationPublished(TENANT, item.associationId(), ACTOR, NOW),
                new EstablishmentAssociationInactivated(TENANT, item.associationId(), ACTOR, NOW),
                new EstablishmentAssociationReactivated(TENANT, item.associationId(), laterActor.actorId(), NOW.plusSeconds(60)));
    }

    @Test void retryWithTheSameKeyCreatesOneAssociation() {
        var first = register.register(admin, "register-key", command());
        inactivate.inactivate(admin, first.association().associationId());
        var retry = register.register(admin, "register-key", command());
        assertThat(retry.created()).isFalse();
        assertThat(retry.association().associationId()).isEqualTo(first.association().associationId());
        assertThat(retry.association().associationStatus()).isEqualTo(AssociationStatus.INACTIVE);
        assertThat(repository.rows).hasSize(1);
        assertThat(keys.entries).hasSize(1);
        assertThat(repository.outbox).hasSize(2);
    }

    @Test void theSameKeyOfTwoTenantsCreatesSeparateAssociations() {
        var first = register.register(admin, "register-key", command());
        var second = register.register(new EstablishmentActor(OTHER, ACTOR, TENANT_ADMINISTRATOR), "register-key", command());
        assertThat(second.association().associationId()).isNotEqualTo(first.association().associationId());
        assertThat(second.association().tenantId()).isEqualTo(OTHER);
        assertThat(repository.rows).hasSize(2);
    }

    @Test void concurrentRetriesCreateOnlyOneAssociation() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<EstablishmentRegistration> intention = () -> register.register(admin, "register-key", command());
            var results = pool.invokeAll(List.of(intention, intention));
            assertThat(results.get(0).get().association().associationId()).isEqualTo(results.get(1).get().association().associationId());
            assertThat(repository.rows).hasSize(1);
            assertThat(repository.outbox).hasSize(1);
        }
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", "short"})
    void aMissingOrInvalidKeyLeavesNothingWritten(String key) {
        assertThatThrownBy(() -> register.register(admin, key, command())).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.field()).isEqualTo("Idempotency-Key"));
        assertThat(repository.rows).isEmpty(); assertThat(keys.entries).isEmpty(); assertThat(repository.outbox).isEmpty();
    }

    @Test void anInvalidRegistrationLeavesNoKeyAssociationOrEvent() {
        var invalid = new RegisterAssociatedEstablishment.Command("HOSTEL", "Sample establishment", "Contact details", "opaque-reference");
        assertThatThrownBy(() -> register.register(admin, "register-key", invalid)).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.field()).isEqualTo("establishmentType"));
        assertThat(repository.rows).isEmpty(); assertThat(keys.entries).isEmpty(); assertThat(repository.outbox).isEmpty();
        assertThat(register.register(admin, "register-key", command()).created()).isTrue();
    }

    @Test void failedRegistrationRollsBackTheKeyAssociationAndEvents() {
        keys.failAfterCreate = true;
        assertThatThrownBy(() -> register.register(admin, "register-key", command())).hasMessage("key write rejected");
        assertThat(repository.rows).isEmpty(); assertThat(keys.entries).isEmpty(); assertThat(repository.outbox).isEmpty();
        assertThat(register.register(admin, "register-key", command()).created()).isTrue();
        assertThat(repository.outbox).hasSize(1);
    }

    @Test void collaboratorIsDeniedRegistration() {
        assertThatThrownBy(() -> register.register(actor(OPERATIONAL_COLLABORATOR), "register-key", command()))
                .isInstanceOf(EstablishmentAccessDeniedException.class);
        assertThat(repository.rows).isEmpty(); assertThat(keys.entries).isEmpty();
    }
    @Test void collaboratorIsDeniedInactivation() {
        var item = plant(AssociationStatus.ACTIVE);
        assertThatThrownBy(() -> inactivate.inactivate(actor(OPERATIONAL_COLLABORATOR), item.associationId()))
                .isInstanceOf(EstablishmentAccessDeniedException.class);
        assertThat(repository.saves).isZero(); assertThat(repository.outbox).isEmpty();
    }
    @Test void collaboratorIsDeniedReactivation() {
        var item = plant(AssociationStatus.INACTIVE);
        assertThatThrownBy(() -> reactivate.reactivate(actor(OPERATIONAL_COLLABORATOR), item.associationId()))
                .isInstanceOf(EstablishmentAccessDeniedException.class);
        assertThat(repository.saves).isZero(); assertThat(repository.outbox).isEmpty();
    }

    @ParameterizedTest @EnumSource(value = EstablishmentActor.Role.class, names = {"MANAGER", "ANALYST"})
    void managerAndAnalystReadButCannotChange(EstablishmentActor.Role role) {
        var item = plant(AssociationStatus.INACTIVE); var reader = actor(role);
        assertThat(get.get(reader, item.associationId()).associationId()).isEqualTo(item.associationId());
        assertThat(list.list(reader, Optional.empty(), Optional.empty(), 1, 20).total()).isEqualTo(1);
        assertThatThrownBy(() -> register.register(reader, "register-key", command())).isInstanceOf(EstablishmentAccessDeniedException.class);
        assertThatThrownBy(() -> inactivate.inactivate(reader, item.associationId())).isInstanceOf(EstablishmentAccessDeniedException.class);
        assertThatThrownBy(() -> reactivate.reactivate(reader, item.associationId())).isInstanceOf(EstablishmentAccessDeniedException.class);
        assertThat(repository.saves).isZero(); assertThat(keys.entries).isEmpty(); assertThat(repository.outbox).isEmpty();
    }

    @ParameterizedTest @EnumSource(value = EstablishmentActor.Role.class, names = {"ACCOUNTANT", "OPERATIONAL_COLLABORATOR"})
    void accountantAndCollaboratorAreDenied(EstablishmentActor.Role role) {
        var denied = actor(role); UUID id = plant(AssociationStatus.ACTIVE).associationId();
        assertThatThrownBy(() -> get.get(denied, id)).isInstanceOf(EstablishmentAccessDeniedException.class);
        assertThatThrownBy(() -> list.list(denied, Optional.empty(), Optional.empty(), 1, 20)).isInstanceOf(EstablishmentAccessDeniedException.class);
        assertThatThrownBy(() -> register.register(denied, "register-key", command())).isInstanceOf(EstablishmentAccessDeniedException.class);
        assertThatThrownBy(() -> inactivate.inactivate(denied, id)).isInstanceOf(EstablishmentAccessDeniedException.class);
        assertThatThrownBy(() -> reactivate.reactivate(denied, id)).isInstanceOf(EstablishmentAccessDeniedException.class);
    }

    @Test void inactiveAssociationRemainsReadableByIdentifier() {
        var item = plant(AssociationStatus.INACTIVE);
        assertThat(get.get(admin, item.associationId()).associationStatus()).isEqualTo(AssociationStatus.INACTIVE);
        assertThat(list.list(admin, Optional.of(AssociationStatus.ACTIVE), Optional.empty(), 1, 20).items()).isEmpty();
        reactivate.reactivate(admin, item.associationId());
        assertThat(list.list(admin, Optional.of(AssociationStatus.ACTIVE), Optional.empty(), 1, 20).items())
                .extracting(AssociatedEstablishment::associationId).containsExactly(item.associationId());
    }

    @ParameterizedTest @ValueSource(strings = {"read", "inactivate", "reactivate"})
    void otherTenantAssociationAnswersAsNonexistentAtTheApplicationBoundary(String operation) {
        UUID foreign = plant(AssociationStatus.INACTIVE).associationId();
        var other = new EstablishmentActor(OTHER, ACTOR, TENANT_ADMINISTRATOR);
        Throwable foreignError = catchThrowable(() -> invoke(operation, other, foreign, repository));
        Throwable missingError = catchThrowable(() -> invoke(operation, other, UUID.randomUUID(), repository));
        assertThat(foreignError).isInstanceOf(AssociatedEstablishmentNotFoundException.class);
        assertThat(missingError).isInstanceOf(AssociatedEstablishmentNotFoundException.class).hasMessage(foreignError.getMessage());
        assertThat(repository.saves).isZero(); assertThat(repository.outbox).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"read", "inactivate", "reactivate"})
    void aRepositoryThatForgetsTheTenantPredicateStillLeaksNothing(String operation) {
        var item = plant(AssociationStatus.INACTIVE);
        var broken = new InMemoryAssociatedEstablishmentRepository() {
            @Override public Optional<AssociatedEstablishment> findByTenantAndId(UUID tenant, UUID id) { return Optional.of(copy(item)); }
        };
        assertThatThrownBy(() -> invoke(operation, new EstablishmentActor(OTHER, ACTOR, TENANT_ADMINISTRATOR), item.associationId(), broken))
                .isInstanceOf(AssociatedEstablishmentNotFoundException.class);
        assertThat(broken.saves).isZero(); assertThat(broken.outbox).isEmpty();
    }

    @Test void listingIsTenantScopedAndRejectsARepositoryThatReturnsForeignItems() {
        plant(AssociationStatus.ACTIVE);
        var other = new EstablishmentActor(OTHER, ACTOR, TENANT_ADMINISTRATOR);
        assertThat(list.list(other, Optional.empty(), Optional.empty(), 1, 20).total()).isZero();
        var broken = new InMemoryAssociatedEstablishmentRepository() {
            @Override public EstablishmentPage listByTenant(UUID tenant, Optional<AssociationStatus> status,
                    Optional<EstablishmentType> type, int page, int limit) {
                return repository.listByTenant(TENANT, status, type, page, limit);
            }
        };
        assertThatThrownBy(() -> new ListAssociatedEstablishmentsUseCase(broken).list(other, Optional.empty(), Optional.empty(), 1, 20))
                .isInstanceOf(AssociatedEstablishmentNotFoundException.class);
    }

    @Test void repeatedTransitionsWriteNeitherTheStateNorAnotherEvent() {
        var item = plant(AssociationStatus.ACTIVE);
        reactivate.reactivate(admin, item.associationId()); assertThat(repository.saves).isZero();
        inactivate.inactivate(admin, item.associationId()); inactivate.inactivate(admin, item.associationId());
        assertThat(repository.saves).isEqualTo(1); assertThat(repository.outbox).hasSize(1);
    }

    @Test void aFailedSaveLeavesTheStoredAssociationAndOutboxUntouched() {
        var item = plant(AssociationStatus.ACTIVE); repository.failNextSave = true;
        assertThatThrownBy(() -> inactivate.inactivate(admin, item.associationId())).hasMessage("save rejected");
        assertThat(get.get(admin, item.associationId()).associationStatus()).isEqualTo(AssociationStatus.ACTIVE);
        assertThat(repository.outbox).isEmpty(); assertThat(repository.saves).isZero();
    }

    @Test void aFailedReactivationEmitsNoEventAndDoesNotSave() {
        var invalid = AssociatedEstablishment.restore(new UUID(0, 111), TENANT, EstablishmentType.HOTEL,
                "Sample establishment", " ", "opaque-reference", AssociationStatus.INACTIVE, NOW, NOW);
        repository.plant(invalid);
        assertThatThrownBy(() -> reactivate.reactivate(admin, invalid.associationId())).isInstanceOf(BusinessRuleViolation.class);
        assertThat(get.get(admin, invalid.associationId()).associationStatus()).isEqualTo(AssociationStatus.INACTIVE);
        assertThat(repository.outbox).isEmpty(); assertThat(repository.saves).isZero();
    }

    @ParameterizedTest @MethodSource("invalidPages")
    void listingRejectsUnboundedOrInvalidPages(int page, int limit, String field) {
        assertThatThrownBy(() -> list.list(admin, Optional.empty(), Optional.empty(), page, limit))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.field()).isEqualTo(field));
    }
    static Stream<Arguments> invalidPages() {
        return Stream.of(Arguments.of(0, 20, "page"), Arguments.of(1, 0, "limit"), Arguments.of(1, 101, "limit"));
    }
    private EstablishmentActor actor(EstablishmentActor.Role role) { return new EstablishmentActor(TENANT, ACTOR, role); }
    private RegisterAssociatedEstablishment.Command command() {
        return new RegisterAssociatedEstablishment.Command("HOTEL", "Sample establishment", "Contact details", "opaque-reference");
    }
    private AssociatedEstablishment plant(AssociationStatus status) {
        var item = AssociatedEstablishment.restore(new UUID(0, 111), TENANT, EstablishmentType.HOTEL,
                "Sample establishment", "Contact details", "opaque-reference", status, NOW, NOW);
        repository.plant(item); return item;
    }
    private void invoke(String operation, EstablishmentActor actor, UUID id, InMemoryAssociatedEstablishmentRepository store) {
        switch (operation) {
            case "read" -> new GetAssociatedEstablishmentUseCase(store).get(actor, id);
            case "inactivate" -> new InactivateAssociatedEstablishmentUseCase(store, clock).inactivate(actor, id);
            case "reactivate" -> new ReactivateAssociatedEstablishmentUseCase(store, clock).reactivate(actor, id);
            default -> throw new AssertionError("Unknown test operation");
        }
    }
}
