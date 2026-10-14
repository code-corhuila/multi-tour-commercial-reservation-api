package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTermsChange;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

class CapacityUseCasesTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private final RecordingRepository repository = new RecordingRepository();
    private final Instant now = Instant.parse("2026-10-10T10:00:00Z");
    private final CapacityRows capacity = new CapacityRows();
    private final UpdateCommercialTermsUseCase update = new UpdateCommercialTermsUseCase(repository,
            Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC), capacity::maxHeldSeats);

    @BeforeEach void plant() {
        repository.plant(CatalogService.register(id, tenant, CatalogServiceType.TOUR, "Capacity test tour",
                CommercialTerms.of(100L, 50L, 18, false, false), now));
    }

    @ParameterizedTest @EnumSource(CatalogStatus.class)
    void capacityBelowHeldSeatsIsRejectedAndNothingIsWritten(CatalogStatus status) {
        if (status == CatalogStatus.INACTIVE) {
            var inactive = stored();
            inactive.inactivate(UUID.randomUUID(), now); inactive.pullEvents(); repository.plant(inactive);
        }
        capacity.held = List.of(12);
        assertThatThrownBy(() -> update.update(tenant, id, change(10)))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("capacity")
                .hasMessageContaining("10").hasMessageContaining("12").hasMessageContaining("held");
        assertThat(repository.saves).isZero();
        assertThat(stored().terms().configuredCapacity()).isEqualTo(18);
        assertThat(stored().updatedAt()).isEqualTo(now);
        assertThat(repository.outbox()).isEmpty();
    }

    @ParameterizedTest @ValueSource(ints = {12, 13})
    void capacityAtOrAboveHeldSeatsIsAccepted(int requested) {
        capacity.held = List.of(12);
        update.update(tenant, id, change(requested));
        assertThat(stored().terms().configuredCapacity()).isEqualTo(requested);
        assertThat(capacity.reads).isEqualTo(1);
        assertThat(repository.saves).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(ints = {18, 20})
    void unchangedOrIncreasedCapacityDoesNotReadHeldSeats(int requested) {
        capacity.held = List.of(100);
        update.update(tenant, id, change(requested));
        assertThat(capacity.reads).isZero();
        assertThat(stored().terms().configuredCapacity()).isEqualTo(requested);
    }

    @Test void omittedCapacityDoesNotReadHeldSeats() {
        capacity.held = List.of(100);
        update.update(tenant, id, new CommercialTermsChange(200L, null, null, null, null));
        assertThat(capacity.reads).isZero();
        assertThat(stored().terms().configuredCapacity()).isEqualTo(18);
    }

    @Test void noDepartureRowsMeansZeroHeldSeats() {
        update.update(tenant, id, change(1));
        assertThat(stored().terms().configuredCapacity()).isEqualTo(1);
        assertThat(capacity.reads).isEqualTo(1);
    }

    @Test void departuresAreComparedByMaximumInsteadOfSum() {
        capacity.held = List.of(5, 12);
        update.update(tenant, id, change(12));
        assertThat(stored().terms().configuredCapacity()).isEqualTo(12);
        assertThat(capacity.reads).isEqualTo(1);
    }

    @Test void capacityReadsUseTheTenantAndServiceOfTheRequest() {
        capacity.held = List.of(12);
        update.update(tenant, id, change(13));
        assertThat(capacity.tenant).isEqualTo(tenant);
        assertThat(capacity.service).isEqualTo(id);
    }

    @Test void aForeignServiceIsRejectedBeforeReadingCapacity() {
        assertThatThrownBy(() -> update.update(UUID.randomUUID(), id, change(10)))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThat(capacity.reads).isZero();
        assertThat(repository.saves).isZero();
    }

    @ParameterizedTest @ValueSource(ints = {0, -1})
    void malformedCapacityKeepsFieldValidation(int requested) {
        capacity.held = List.of(12);
        assertThatThrownBy(() -> update.update(tenant, id, change(requested))).isInstanceOf(DomainException.class);
        assertThat(capacity.reads).isZero();
        assertThat(repository.saves).isZero();
    }

    private CatalogService stored() { return repository.findByTenantAndId(tenant, id).orElseThrow(); }
    private static CommercialTermsChange change(int capacity) {
        return new CommercialTermsChange(null, null, capacity, null, null);
    }
    private static final class RecordingRepository extends InMemoryCatalogServiceRepository {
        int saves;
        @Override public void save(CatalogService service) { saves++; super.save(service); }
    }
    private static final class CapacityRows {
        List<Integer> held = List.of();
        int reads;
        UUID tenant;
        UUID service;
        int maxHeldSeats(UUID tenant, UUID service) {
            reads++; this.tenant = tenant; this.service = service;
            return held.stream().mapToInt(Integer::intValue).max().orElse(0);
        }
    }
}
