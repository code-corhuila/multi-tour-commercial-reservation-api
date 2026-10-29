package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantEventOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEventType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// api spec 021 T-01/T-02 at the core: RN-TSC-02 (the id and the copy together), RN-TSC-03, RN-TSC-04 with fake ports.
class ApplyTenantEventTest {

    private static final UUID TENANT = new UUID(1, 1);
    private static final Instant T0 = Instant.parse("2026-10-29T14:00:00Z");

    private final TenantEventFakes store = new TenantEventFakes();
    private final ApplyTenantEventUseCase apply = new ApplyTenantEventUseCase(store, store, store);

    private static TenantEvent created(Instant at) {
        return new TenantEvent(UUID.randomUUID(), TenantEventType.TENANT_CREATED, TENANT, at, "agencia-ficticia",
                TenantStatus.ACTIVE, "America/Bogota", "CO");
    }

    private static TenantEvent inactivated(Instant at) {
        return new TenantEvent(UUID.randomUUID(), TenantEventType.TENANT_INACTIVATED, TENANT, at, null, null, null, null);
    }

    @Test
    void theFirstDeliveryAppliesAndARepeatOfTheIdChangesNothing() {
        TenantEvent event = created(T0);

        assertThat(apply.apply(event)).isEqualTo(TenantEventOutcome.APPLIED);
        assertThat(store.processed).containsExactly(event.eventId());
        assertThat(apply.apply(event)).isEqualTo(TenantEventOutcome.NOT_APPLIED);
        assertThat(store.copies.get(TENANT).lastEventAt()).isEqualTo(T0);
    }

    @Test
    void anOlderOrEqualEventLeavesTheCopyButItsIdIsRecorded() {
        apply.apply(created(T0));
        TenantEvent same = inactivated(T0);
        TenantEvent older = inactivated(T0.minusSeconds(5));

        assertThat(apply.apply(same)).isEqualTo(TenantEventOutcome.NOT_APPLIED);
        assertThat(apply.apply(older)).isEqualTo(TenantEventOutcome.NOT_APPLIED);

        assertThat(store.copies.get(TENANT).status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(store.processed).contains(same.eventId(), older.eventId());
    }

    @Test
    void aNewerEventMovesTheCopyForward() {
        apply.apply(created(T0));

        assertThat(apply.apply(inactivated(T0.plusSeconds(1)))).isEqualTo(TenantEventOutcome.APPLIED);

        assertThat(store.copies.get(TENANT).status()).isEqualTo(TenantStatus.INACTIVE);
    }

    @Test
    void anEventThatCannotCreateTheCopyIsRejectedAndLeavesNoId() {
        TenantEvent incomplete = inactivated(T0);

        assertThatThrownBy(() -> apply.apply(incomplete)).isInstanceOf(DomainException.class);

        assertThat(store.copies).isEmpty();
        assertThat(store.processed).isEmpty();
    }

    @Test
    void aFailureAfterTheIdWasRecordedLeavesNeitherTheIdNorTheCopy() {
        store.failAfterRecord = new IllegalStateException("forced");

        assertThatThrownBy(() -> apply.apply(created(T0))).isInstanceOf(IllegalStateException.class);

        assertThat(store.copies).isEmpty();
        assertThat(store.processed).isEmpty();
    }
}
