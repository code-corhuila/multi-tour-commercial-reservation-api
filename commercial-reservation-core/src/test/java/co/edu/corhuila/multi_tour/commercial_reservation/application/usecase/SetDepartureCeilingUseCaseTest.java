package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CeilingState;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureCeilings;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// HU-COM-012 amendment 2026-11-10, R1 (AC-12.R1.1 to AC-12.R1.6): the ceiling of a departure for the saga change-departure-vehicles.
class SetDepartureCeilingUseCaseTest {
    private static final String KEY = "ceiling-key-0001";

    private final UUID tenant = UUID.randomUUID(), salida = UUID.randomUUID();
    private final Ceilings ceilings = new Ceilings();
    private final Keys keys = new Keys();
    private final SetDepartureCeilingUseCase useCase = new SetDepartureCeilingUseCase(ceilings, keys, keys);

    private void plant(int ceiling, int held, boolean open) {
        ceilings.rows.put(salida, new CeilingState(ceiling, held, open));
    }

    @Test void AC_12_R1_1_lowersTheCeilingAndAnswersThePreviousOne() {
        plant(10, 4, true);
        var done = useCase.set(tenant, salida, 6, KEY);
        assertThat(done.previousCeiling()).isEqualTo(10);
        assertThat(done.ceiling()).isEqualTo(6);
        assertThat(done.seatsHeld()).isEqualTo(4);
        assertThat(ceilings.rows.get(salida).ceiling()).isEqualTo(6);
    }

    @Test void AC_12_R1_2_moreSeatsHeldThanTheCeilingChangesNothingAndTheKeyIsNotSpent() {
        plant(10, 7, true);
        assertThatThrownBy(() -> useCase.set(tenant, salida, 6, KEY)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(ceilings.rows.get(salida).ceiling()).isEqualTo(10);
        assertThat(keys.stored).isEmpty();
        plant(10, 5, true);
        assertThat(useCase.set(tenant, salida, 6, KEY).ceiling()).isEqualTo(6);
    }

    @Test void AC_12_R1_3_theCompensationRestoresThePreviousCeilingWithAnotherKey() {
        plant(10, 4, true);
        var lowered = useCase.set(tenant, salida, 6, KEY);
        var restored = useCase.set(tenant, salida, lowered.previousCeiling(), "ceiling-key-0002");
        assertThat(restored.previousCeiling()).isEqualTo(6);
        assertThat(restored.ceiling()).isEqualTo(10);
        assertThat(ceilings.rows.get(salida).ceiling()).isEqualTo(10);
    }

    @Test void AC_12_R1_4_aReplayAnswersTheOriginalPreviousCeilingAndNeverAppliesItAgain() {
        plant(10, 4, true);
        var first = useCase.set(tenant, salida, 6, KEY);
        useCase.set(tenant, salida, 10, "ceiling-key-0002");
        var replay = useCase.set(tenant, salida, 6, KEY);
        assertThat(replay.previousCeiling()).isEqualTo(first.previousCeiling());
        assertThat(replay.ceiling()).isEqualTo(6);
        assertThat(ceilings.rows.get(salida).ceiling()).isEqualTo(10);
    }

    @Test void AC_12_R1_4_theSameKeyWithAnotherCeilingOrAnotherDepartureIsRefused() {
        plant(10, 4, true);
        useCase.set(tenant, salida, 6, KEY);
        assertThatThrownBy(() -> useCase.set(tenant, salida, 7, KEY)).isInstanceOf(BusinessRuleViolation.class);
        UUID other = UUID.randomUUID();
        ceilings.rows.put(other, new CeilingState(10, 0, true));
        assertThatThrownBy(() -> useCase.set(tenant, other, 6, KEY)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(ceilings.rows.get(other).ceiling()).isEqualTo(10);
    }

    @Test void AC_12_R1_4_settingTheValueTheDepartureAlreadyHasChangesNothing() {
        plant(10, 4, true);
        var done = useCase.set(tenant, salida, 10, KEY);
        assertThat(done.previousCeiling()).isEqualTo(10);
        assertThat(done.ceiling()).isEqualTo(10);
        assertThat(ceilings.writes).isZero();
    }

    @Test void AC_12_R1_5_aCeilingOutOfRangeIsAValidationError() {
        plant(10, 4, true);
        assertThatThrownBy(() -> useCase.set(tenant, salida, -1, KEY)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> useCase.set(tenant, salida, 100_001, KEY)).isInstanceOf(DomainException.class);
        assertThat(ceilings.writes).isZero();
    }

    @Test void AC_12_R1_6_aMissingDepartureIsNotFoundAndLeavesNoKey() {
        assertThatThrownBy(() -> useCase.set(tenant, salida, 6, KEY)).isInstanceOf(DepartureNotFoundException.class);
        assertThat(keys.stored).isEmpty();
    }

    @Test void AC_12_R1_6_aFinalDepartureIsRefused() {
        plant(10, 0, false);
        assertThatThrownBy(() -> useCase.set(tenant, salida, 6, KEY)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(ceilings.rows.get(salida).ceiling()).isEqualTo(10);
    }

    private static final class Ceilings implements DepartureCeilings {
        final Map<UUID, CeilingState> rows = new HashMap<>();
        int writes;

        @Override public Optional<CeilingState> find(UUID tenantId, UUID salidaId) { return Optional.ofNullable(rows.get(salidaId)); }
        @Override public Optional<CeilingState> findForUpdate(UUID tenantId, UUID salidaId) { return find(tenantId, salidaId); }
        @Override public void setCeiling(UUID tenantId, UUID salidaId, int ceiling) {
            var row = rows.get(salidaId);
            rows.put(salidaId, new CeilingState(ceiling, row.seatsHeld(), row.open()));
            writes++;
        }
    }

    // The keys and the transaction in one fake: a failed unit of work puts the keys back as they were, as the database does.
    private static final class Keys implements IdempotencyStore, PaymentTransaction {
        Map<String, UUID> stored = new HashMap<>();

        @Override public Optional<UUID> find(UUID tenantId, String key) { return Optional.ofNullable(stored.get(tenantId + key)); }
        @Override public boolean register(UUID tenantId, String key, String resourceType, UUID resourceId) {
            return stored.putIfAbsent(tenantId + key, resourceId) == null;
        }
        @Override public <T> T inTransaction(Supplier<T> work) {
            var before = new HashMap<>(stored);
            try {
                return work.get();
            } catch (RuntimeException failure) {
                stored = before;
                throw failure;
            }
        }
    }
}
