package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.MirrorOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SalidaEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-006 T-07 (use case only): RN-11, AC-06.14, AC-06.16. The transport of the events is not built here.
class MirrorSalidaStateTest extends PublicOfferFixture {

    private static final Instant STARTS = Instant.parse("2026-10-20T08:00:00Z");
    private static final UUID DEPARTURE = new UUID(7, 1);

    private final DepartureFakes store = new DepartureFakes();
    private final MirrorSalidaStateUseCase mirror = new MirrorSalidaStateUseCase(store, store, store, repository);
    private static final Instant OCCURRED = Instant.parse("2026-11-03T10:00:00Z");
    private long eventSeq = 1;
    private long clock = 0;

    private UUID event() {
        return new UUID(8, eventSeq++);
    }

    // The occurredAt of the events of these tests: each call is one second after the last, unless a test names its own.
    private Instant tick() {
        return at(++clock);
    }

    private static Instant at(long seconds) {
        return OCCURRED.plusSeconds(seconds);
    }

    private MirrorOutcome scheduled(CatalogService tour) {
        return mirror.apply(new SalidaEvent.Scheduled(event(), tour.tenantId(), DEPARTURE, tour.serviceId(), STARTS, tick()));
    }

    private DepartureState row() {
        return store.rows.get(DEPARTURE);
    }

    @Test
    void aScheduledEventCreatesTheCapacityRowWithStartAndStatus() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, false);

        assertThat(scheduled(tour)).isEqualTo(MirrorOutcome.APPLIED);

        assertThat(row()).isEqualTo(new DepartureState(DEPARTURE, TENANT, tour.serviceId(), STARTS,
                DepartureStatus.SCHEDULED, 18, 0, at(1)));
    }

    @Test
    void aTourCapacityWithMandatoryTransportStartsAtZero() {
        CatalogService tour = plantTourWith(TENANT, "With bus", 100_000, 18, true);

        scheduled(tour);

        assertThat(row().seats().effectiveCapacity()).isZero();
    }

    @Test
    void aDepartureEventDeliveredTwiceHasOneEffect() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, false);
        UUID started = event();
        scheduled(tour);

        var first = mirror.apply(new SalidaEvent.ExecutionStarted(started, TENANT, DEPARTURE, tick()));
        store.journal.clear();
        var second = mirror.apply(new SalidaEvent.ExecutionStarted(started, TENANT, DEPARTURE, tick()));

        assertThat(first).isEqualTo(MirrorOutcome.APPLIED);
        assertThat(second).isEqualTo(MirrorOutcome.DUPLICATE);
        assertThat(row().status()).isEqualTo(DepartureStatus.IN_EXECUTION);
        assertThat(store.journal).noneMatch(entry -> entry.startsWith("save"));
    }

    @Test
    void theEffectAndTheProcessedMarkCommitInOneTransaction() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, false);

        scheduled(tour);

        assertThat(store.journal.get(0)).isEqualTo("begin");
        assertThat(store.journal).hasSize(4).last().isEqualTo("commit");
        assertThat(store.journal).anyMatch(entry -> entry.startsWith("save")).anyMatch(entry -> entry.startsWith("record"));
    }

    @Test
    void aCancelledEventTakesTheDepartureOutOfTheOffer() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, false);
        scheduled(tour);

        var outcome = mirror.apply(new SalidaEvent.Cancelled(event(), TENANT, DEPARTURE, tick()));

        assertThat(outcome).isEqualTo(MirrorOutcome.APPLIED);
        assertThat(row().status()).isEqualTo(DepartureStatus.CANCELLED);
    }

    @Test
    void aLateEventNeverMovesADepartureBackwards() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, false);
        scheduled(tour);
        mirror.apply(new SalidaEvent.Finalized(event(), TENANT, DEPARTURE, tick()));

        assertThat(mirror.apply(new SalidaEvent.ExecutionStarted(event(), TENANT, DEPARTURE, tick())))
                .isEqualTo(MirrorOutcome.IGNORED_OUT_OF_ORDER);
        assertThat(mirror.apply(new SalidaEvent.Cancelled(event(), TENANT, DEPARTURE, tick())))
                .isEqualTo(MirrorOutcome.IGNORED_OUT_OF_ORDER);
        assertThat(scheduled(tour)).isEqualTo(MirrorOutcome.IGNORED_OUT_OF_ORDER);

        assertThat(row().status()).isEqualTo(DepartureStatus.FINALIZED);
        assertThat(row().startsAt()).isEqualTo(STARTS);
    }

    @Test
    void aScheduledEventOnAnExistingRowChangesNothing() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, false);
        scheduled(tour);
        mirror.apply(new SalidaEvent.ExecutionStarted(event(), TENANT, DEPARTURE, tick()));

        mirror.apply(new SalidaEvent.Scheduled(event(), TENANT, DEPARTURE, tour.serviceId(), STARTS.plusSeconds(3600), tick()));

        assertThat(row().status()).isEqualTo(DepartureStatus.IN_EXECUTION);
        assertThat(row().startsAt()).isEqualTo(STARTS);
    }

    @Test
    void anEventForADepartureWithoutRowAwaitsItsRowAndCanBeRedeliveredAfterTheScheduledOne() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, false);
        UUID cancelled = event();

        assertThat(mirror.apply(new SalidaEvent.Cancelled(cancelled, TENANT, DEPARTURE, tick())))
                .isEqualTo(MirrorOutcome.AWAITING_DEPARTURE);
        assertThat(store.rows).isEmpty();
        assertThat(store.processed).doesNotContain(cancelled);

        scheduled(tour);
        assertThat(mirror.apply(new SalidaEvent.Cancelled(cancelled, TENANT, DEPARTURE, tick()))).isEqualTo(MirrorOutcome.APPLIED);
        assertThat(row().status()).isEqualTo(DepartureStatus.CANCELLED);
    }

    @Test
    void aScheduledEventForAnUnknownForeignOrNonTourServiceCreatesNothing() {
        CatalogService foreign = plantTourWith(OTHER_TENANT, "Foreign", 100_000, 10, false);
        CatalogService transport = plantTransport(TENANT, "Shuttle");

        assertThat(mirror.apply(new SalidaEvent.Scheduled(event(), TENANT, DEPARTURE, foreign.serviceId(), STARTS, tick())))
                .isEqualTo(MirrorOutcome.IGNORED_UNKNOWN);
        assertThat(mirror.apply(new SalidaEvent.Scheduled(event(), TENANT, DEPARTURE, transport.serviceId(), STARTS, tick())))
                .isEqualTo(MirrorOutcome.IGNORED_UNKNOWN);
        assertThat(mirror.apply(new SalidaEvent.Scheduled(event(), TENANT, DEPARTURE, new UUID(9, 9), STARTS, tick())))
                .isEqualTo(MirrorOutcome.IGNORED_UNKNOWN);
        assertThat(store.rows).isEmpty();
    }

    @Test
    void anEventOfAnotherTenantNeverTouchesTheRow() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, false);
        scheduled(tour);

        var outcome = mirror.apply(new SalidaEvent.Cancelled(event(), OTHER_TENANT, DEPARTURE, tick()));

        assertThat(outcome).isEqualTo(MirrorOutcome.AWAITING_DEPARTURE);
        assertThat(row().status()).isEqualTo(DepartureStatus.SCHEDULED);
    }

    @Test
    void aVehicleCapacityEventSetsTheEffectiveCapacityToTheLowerValueNeverBelowHeldSeats() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, true);
        scheduled(tour);
        assertThat(row().seats().effectiveCapacity()).isZero();

        vehicles(12);
        assertThat(row().seats().effectiveCapacity()).isEqualTo(12);

        vehicles(40);
        assertThat(row().seats().effectiveCapacity()).isEqualTo(18);

        store.plant(new DepartureState(DEPARTURE, TENANT, tour.serviceId(), STARTS, DepartureStatus.SCHEDULED, 18, 15));
        vehicles(10);
        assertThat(row().seats().effectiveCapacity()).isEqualTo(15);
        assertThat(row().seats().heldSeats()).isEqualTo(15);
    }

    @Test
    void aVehicleEventDoesNotChangeTheStatus() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, true);
        scheduled(tour);
        mirror.apply(new SalidaEvent.ExecutionStarted(event(), TENANT, DEPARTURE, tick()));

        vehicles(12);

        assertThat(row().status()).isEqualTo(DepartureStatus.IN_EXECUTION);
        assertThat(row().seats().effectiveCapacity()).isEqualTo(12);
    }

    @Test
    void aVehicleEventForAFinalizedOrCancelledDepartureIsIgnoredButMarkedProcessed() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, true);
        scheduled(tour);
        vehicles(12);
        mirror.apply(new SalidaEvent.Finalized(event(), TENANT, DEPARTURE, tick()));
        assertVehiclesAreIgnored(12);

        store.rows.clear();
        scheduled(tour);
        vehicles(10);
        mirror.apply(new SalidaEvent.Cancelled(event(), TENANT, DEPARTURE, tick()));
        assertVehiclesAreIgnored(10);
    }

    private void assertVehiclesAreIgnored(int keptCapacity) {
        UUID late = event();
        store.journal.clear();

        var outcome = mirror.apply(new SalidaEvent.VehiclesChanged(late, TENANT, DEPARTURE, 5, tick()));

        assertThat(outcome).isEqualTo(MirrorOutcome.IGNORED_OUT_OF_ORDER);
        assertThat(row().seats().effectiveCapacity()).isEqualTo(keptCapacity);
        assertThat(store.journal).noneMatch(entry -> entry.startsWith("save"));
        assertThat(store.processed).contains(late);
        assertThat(mirror.apply(new SalidaEvent.VehiclesChanged(late, TENANT, DEPARTURE, 5, tick())))
                .isEqualTo(MirrorOutcome.DUPLICATE);
    }

    @Test
    void aVehicleEventForADepartureWithNoRowYetAwaitsItsRowAndStaysUnmarked() {
        UUID early = event();

        var outcome = mirror.apply(new SalidaEvent.VehiclesChanged(early, TENANT, DEPARTURE, 5, tick()));

        assertThat(outcome).isEqualTo(MirrorOutcome.AWAITING_DEPARTURE);
        assertThat(store.rows).isEmpty();
        assertThat(store.processed).doesNotContain(early);
    }

    // DP-16 and DD-8 (answered by the Project Lead): the row keeps the occurredAt of the last event applied.
    @Test
    void theRowKeepsTheOccurredAtOfTheLastEventApplied() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, true);
        mirror.apply(new SalidaEvent.Scheduled(event(), TENANT, DEPARTURE, tour.serviceId(), STARTS, at(10)));
        assertThat(row().lastEventOccurredAt()).isEqualTo(at(10));

        mirror.apply(new SalidaEvent.VehiclesChanged(event(), TENANT, DEPARTURE, 12, at(20)));
        assertThat(row().lastEventOccurredAt()).isEqualTo(at(20));

        mirror.apply(new SalidaEvent.ExecutionStarted(event(), TENANT, DEPARTURE, at(30)));
        assertThat(row().lastEventOccurredAt()).isEqualTo(at(30));
    }

    @Test
    void anOlderVehicleEventNeverOverwritesTheCapacityOfANewerOne() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, true);
        mirror.apply(new SalidaEvent.Scheduled(event(), TENANT, DEPARTURE, tour.serviceId(), STARTS, at(10)));
        mirror.apply(new SalidaEvent.VehiclesChanged(event(), TENANT, DEPARTURE, 12, at(30)));
        UUID older = event();
        store.journal.clear();

        var outcome = mirror.apply(new SalidaEvent.VehiclesChanged(older, TENANT, DEPARTURE, 8, at(20)));

        assertThat(outcome).isEqualTo(MirrorOutcome.IGNORED_OUT_OF_ORDER);
        assertThat(row().seats().effectiveCapacity()).isEqualTo(12);
        assertThat(row().lastEventOccurredAt()).isEqualTo(at(30));
        assertThat(store.journal).noneMatch(entry -> entry.startsWith("save"));
        assertThat(store.processed).contains(older);
    }

    @Test
    void anEventWithTheSameOccurredAtAsTheMarkerIsApplied() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, true);
        mirror.apply(new SalidaEvent.Scheduled(event(), TENANT, DEPARTURE, tour.serviceId(), STARTS, at(10)));

        var outcome = mirror.apply(new SalidaEvent.VehiclesChanged(event(), TENANT, DEPARTURE, 12, at(10)));

        assertThat(outcome).isEqualTo(MirrorOutcome.APPLIED);
        assertThat(row().seats().effectiveCapacity()).isEqualTo(12);
    }

    // The forward-only matrix of AC-06.16 keeps deciding the status: a cancellation always applies to an open
    // departure, even when a vehicle change that happened after it was applied first. The marker never goes back.
    @Test
    void aStatusEventOlderThanTheMarkerStillAppliesByTheMatrixAndTheMarkerNeverMovesBack() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, true);
        mirror.apply(new SalidaEvent.Scheduled(event(), TENANT, DEPARTURE, tour.serviceId(), STARTS, at(10)));
        mirror.apply(new SalidaEvent.VehiclesChanged(event(), TENANT, DEPARTURE, 12, at(30)));

        var outcome = mirror.apply(new SalidaEvent.Cancelled(event(), TENANT, DEPARTURE, at(20)));

        assertThat(outcome).isEqualTo(MirrorOutcome.APPLIED);
        assertThat(row().status()).isEqualTo(DepartureStatus.CANCELLED);
        assertThat(row().lastEventOccurredAt()).isEqualTo(at(30));
    }

    @Test
    void anEventIgnoredByTheMatrixDoesNotMoveTheMarker() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, false);
        mirror.apply(new SalidaEvent.Scheduled(event(), TENANT, DEPARTURE, tour.serviceId(), STARTS, at(10)));
        mirror.apply(new SalidaEvent.Finalized(event(), TENANT, DEPARTURE, at(20)));

        mirror.apply(new SalidaEvent.ExecutionStarted(event(), TENANT, DEPARTURE, at(40)));

        assertThat(row().status()).isEqualTo(DepartureStatus.FINALIZED);
        assertThat(row().lastEventOccurredAt()).isEqualTo(at(20));
    }

    @Test
    void aRowBornBeforeTheMarkerAcceptsItsFirstEventAndGainsTheMarker() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 18, true);
        store.plant(new DepartureState(DEPARTURE, TENANT, tour.serviceId(), STARTS, DepartureStatus.SCHEDULED, 0, 0));
        assertThat(row().lastEventOccurredAt()).isNull();

        var outcome = mirror.apply(new SalidaEvent.VehiclesChanged(event(), TENANT, DEPARTURE, 12, at(5)));

        assertThat(outcome).isEqualTo(MirrorOutcome.APPLIED);
        assertThat(row().lastEventOccurredAt()).isEqualTo(at(5));
    }

    private void vehicles(int total) {
        mirror.apply(new SalidaEvent.VehiclesChanged(event(), TENANT, DEPARTURE, total, tick()));
    }
}
