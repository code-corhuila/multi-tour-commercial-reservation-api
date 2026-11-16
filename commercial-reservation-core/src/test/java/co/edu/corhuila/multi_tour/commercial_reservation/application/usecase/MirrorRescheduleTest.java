package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.MirrorOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SalidaEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Spec 027 (decision of 2026-11-15): SalidaRescheduled moves the start and the estimated end of the capacity row (alternative A: the
// reservations on it stay the same and show the new date); a different service is alternative B, detected and refused.
class MirrorRescheduleTest extends PublicOfferFixture {
    private static final Instant STARTS = Instant.parse("2026-10-20T08:00:00Z");
    private static final Instant NEW_START = Instant.parse("2026-10-27T09:00:00Z");
    private static final Instant NEW_END = Instant.parse("2026-10-27T17:00:00Z");
    private static final Instant OCCURRED = Instant.parse("2026-11-03T10:00:00Z");
    private static final UUID DEPARTURE = new UUID(7, 1);

    private final DepartureFakes store = new DepartureFakes();
    private final MirrorSalidaStateUseCase mirror = new MirrorSalidaStateUseCase(store, store, store, repository);
    private long seq = 1;

    private CatalogService scheduled() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, false);
        mirror.apply(new SalidaEvent.Scheduled(new UUID(8, seq++), TENANT, DEPARTURE, tour.serviceId(), STARTS, OCCURRED));
        return tour;
    }

    private SalidaEvent.Rescheduled moved(UUID eventId, UUID serviceId, long seconds) {
        return new SalidaEvent.Rescheduled(eventId, TENANT, DEPARTURE, serviceId, NEW_START, NEW_END, OCCURRED.plusSeconds(seconds));
    }

    private DepartureState row() {
        return store.rows.get(DEPARTURE);
    }

    @Test
    void updatesTheStartAndTheEndOfTheRow() {
        CatalogService tour = scheduled();

        assertThat(mirror.apply(moved(new UUID(8, 50), tour.serviceId(), 10))).isEqualTo(MirrorOutcome.APPLIED);

        assertThat(row().startsAt()).isEqualTo(NEW_START);
        assertThat(store.ends.get(DEPARTURE)).isEqualTo(NEW_END);
        assertThat(row().status()).isEqualTo(DepartureStatus.SCHEDULED);
        assertThat(row().lastEventOccurredAt()).isEqualTo(OCCURRED.plusSeconds(10));
    }

    @Test
    void keepsTheHeldSeatsWhenTheDateMoves() {
        CatalogService tour = scheduled();
        store.plant(new DepartureState(DEPARTURE, TENANT, tour.serviceId(), STARTS, DepartureStatus.SCHEDULED, 18, 4, OCCURRED));

        mirror.apply(moved(new UUID(8, 50), tour.serviceId(), 10));

        assertThat(row().heldSeats()).isEqualTo(4);
        assertThat(row().effectiveCapacity()).isEqualTo(18);
    }

    @Test
    void ignoresAnOlderRescheduleEvent() {
        CatalogService tour = scheduled();
        mirror.apply(moved(new UUID(8, 50), tour.serviceId(), 100));

        SalidaEvent.Rescheduled older = new SalidaEvent.Rescheduled(new UUID(8, 51), TENANT, DEPARTURE, tour.serviceId(),
                STARTS.plusSeconds(60), NEW_END.plusSeconds(60), OCCURRED.plusSeconds(50));

        assertThat(mirror.apply(older)).isEqualTo(MirrorOutcome.IGNORED_OUT_OF_ORDER);
        assertThat(row().startsAt()).isEqualTo(NEW_START);
        assertThat(store.ends.get(DEPARTURE)).isEqualTo(NEW_END);
    }

    @Test
    void waitsForTheScheduledEventBeforeARescheduleEvent() {
        UUID id = new UUID(8, 60);

        assertThat(mirror.apply(moved(id, new UUID(9, 9), 10))).isEqualTo(MirrorOutcome.AWAITING_DEPARTURE);

        assertThat(store.rows).isEmpty();
        assertThat(store.processed).doesNotContain(id);
    }

    @Test
    void repeatsTheEventWithoutASecondEffect() {
        CatalogService tour = scheduled();
        UUID id = new UUID(8, 50);
        mirror.apply(moved(id, tour.serviceId(), 10));
        store.journal.clear();

        assertThat(mirror.apply(moved(id, tour.serviceId(), 10))).isEqualTo(MirrorOutcome.DUPLICATE);

        assertThat(store.journal).noneMatch(entry -> entry.startsWith("move") || entry.startsWith("save"));
    }

    @Test
    void ignoresARescheduleOfAFinalDeparture() {
        CatalogService tour = scheduled();
        mirror.apply(new SalidaEvent.Cancelled(new UUID(8, 70), TENANT, DEPARTURE, OCCURRED.plusSeconds(5)));

        assertThat(mirror.apply(moved(new UUID(8, 50), tour.serviceId(), 10))).isEqualTo(MirrorOutcome.IGNORED_OUT_OF_ORDER);

        assertThat(row().startsAt()).isEqualTo(STARTS);
    }

    @Test
    void refusesARescheduleThatChangesTheService() {
        scheduled();
        UUID id = new UUID(8, 50);

        assertThatThrownBy(() -> mirror.apply(moved(id, new UUID(9, 9), 10))).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageNotContaining(TENANT.toString());

        assertThat(row().startsAt()).isEqualTo(STARTS);
        assertThat(store.processed).doesNotContain(id);
    }
}
