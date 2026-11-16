package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelledDepartureOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OpenReschedulingCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SalidaEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.OpenRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.VoidReschedulingProposals;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// Spec 023 (-api), T-02 (AC-23.1 to AC-23.7): after the mirror marks the departure CANCELLED, one episode of rescheduling opens for every alive reservation of that departure,
// counted from the instant of the event, and the pending proposals that point to the departure are void. The event is marked as received only when every reservation has its
// episode; a failure leaves it unmarked and a redelivery continues. Doubles only; no tenant status is read, so an inactive tenant opens the episodes too (I1).
class HandleCancelledDepartureTest {

    private static final UUID TENANT = new UUID(23, 1), SALIDA = new UUID(23, 2), SERVICE = new UUID(23, 3);
    private static final Instant AT = Instant.parse("2026-11-12T15:00:00Z");

    private final DepartureFakes departures = new DepartureFakes();
    private final List<UUID> alive = new ArrayList<>();
    private final Map<UUID, ReschedulingEpisode> episodes = new LinkedHashMap<>();
    private final List<OpenReschedulingCommand> attempts = new ArrayList<>();
    private final List<String> voided = new ArrayList<>();
    private UUID failingReservation;
    private int pageSize;

    private final DepartureReservationReader reader = (tenantId, departureId, after, limit) -> {
        pageSize = limit;
        return alive.stream().filter(id -> tenantId.equals(TENANT) && departureId.equals(SALIDA) && (after == null || id.compareTo(after) > 0)).sorted().limit(limit).toList();
    };
    private final OpenRescheduling opening = command -> {
        attempts.add(command);
        if (command.reservationId().equals(failingReservation)) throw new IllegalStateException("the episode could not be written");
        return episodes.computeIfAbsent(command.reservationId(), id -> ReschedulingEpisode.open(UUID.randomUUID(), command.tenantId(), id, command.departureId(), command.cancelledAt(), AT));
    };
    private final VoidReschedulingProposals voiding = (tenantId, salidaId, cancelledAt) -> {
        voided.add(salidaId + "@" + cancelledAt);
        return List.of();
    };
    private final PaymentTransaction transaction = new PaymentTransaction() {
        @Override public <T> T inTransaction(Supplier<T> work) { return work.get(); }
    };
    private final HandleCancelledDepartureUseCase useCase = new HandleCancelledDepartureUseCase(departures, departures, reader, opening, voiding, transaction);

    HandleCancelledDepartureTest() {
        departures.plant(new DepartureState(SALIDA, TENANT, SERVICE, AT.plusSeconds(86_400), DepartureStatus.CANCELLED, 10, 0, AT));
    }

    private SalidaEvent.Cancelled event(UUID eventId) {
        return new SalidaEvent.Cancelled(eventId, TENANT, SALIDA, AT);
    }

    private void reservations(int count) {
        for (int i = 1; i <= count; i++) alive.add(new UUID(230, i));
    }

    @Test
    void opensOneEpisodePerAliveReservationOfTheCancelledDeparture() {
        reservations(3);
        UUID eventId = UUID.randomUUID();

        assertThat(useCase.handle(event(eventId))).isEqualTo(CancelledDepartureOutcome.DONE);

        assertThat(episodes).hasSize(3);
        assertThat(departures.processed).containsExactly(eventId);
    }

    @Test
    void walksEveryPageOfAReservationsLargerThanOneBatch() {
        reservations(250);

        assertThat(useCase.handle(event(UUID.randomUUID()))).isEqualTo(CancelledDepartureOutcome.DONE);

        assertThat(episodes).hasSize(250);
        assertThat(pageSize).as("DP-23.3: a batch of 100 per page").isEqualTo(100);
    }

    @Test
    void countsTheDeadlinesFromTheEventInstant() {
        reservations(1);

        useCase.handle(event(UUID.randomUUID()));

        assertThat(attempts).allSatisfy(command -> assertThat(command.cancelledAt()).isEqualTo(AT));
    }

    @Test
    void countsTheDeadlinesFromTheCancelledAtOfThePayloadWhenItComes() {
        // The saga of operations opens the episodes from the same cancelledAt, so both paths give the same 24 h and 72 h clocks (DP-23.2).
        reservations(2);
        Instant cancelledAt = AT.minusSeconds(3_600);

        useCase.handle(new SalidaEvent.Cancelled(UUID.randomUUID(), TENANT, SALIDA, cancelledAt, AT));

        assertThat(attempts).hasSize(2).allSatisfy(command -> assertThat(command.cancelledAt()).isEqualTo(cancelledAt));
        assertThat(voided).containsExactly(SALIDA + "@" + cancelledAt);
    }

    @Test
    void fallsBackToTheEnvelopeInstantWhenThePayloadCarriesNoCancelledAt() {
        reservations(1);

        useCase.handle(new SalidaEvent.Cancelled(UUID.randomUUID(), TENANT, SALIDA, null, AT));

        assertThat(attempts).allSatisfy(command -> assertThat(command.cancelledAt()).isEqualTo(AT));
    }

    @Test
    void marksTheEventOnlyAfterAllEpisodesExist() {
        reservations(3);
        failingReservation = new UUID(230, 2);
        UUID eventId = UUID.randomUUID();

        assertThat(useCase.handle(event(eventId))).isEqualTo(CancelledDepartureOutcome.RETRY_LATER);

        assertThat(departures.processed).as("a failure leaves the event unmarked").isEmpty();
        assertThat(voided).isEmpty();

        failingReservation = null;
        assertThat(useCase.handle(event(eventId))).isEqualTo(CancelledDepartureOutcome.DONE);
        assertThat(episodes).hasSize(3);
        assertThat(departures.processed).containsExactly(eventId);
    }

    @Test
    void repeatsTheEventWithoutASecondEpisode() {
        reservations(2);
        useCase.handle(event(UUID.randomUUID()));

        assertThat(useCase.handle(event(UUID.randomUUID()))).isEqualTo(CancelledDepartureOutcome.DONE);

        assertThat(episodes).as("the opening is idempotent by reservation and departure").hasSize(2);
    }

    @Test
    void skipsCancelledReservationsBecauseTheReaderOnlyListsTheAliveOnes() {
        reservations(2);
        alive.remove(0);

        useCase.handle(event(UUID.randomUUID()));

        assertThat(episodes).hasSize(1);
    }

    @Test
    void voidsThePendingProposalsOfTheCancelledDeparture() {
        useCase.handle(event(UUID.randomUUID()));

        assertThat(voided).containsExactly(SALIDA + "@" + AT);
    }

    @Test
    void aDepartureThatIsNotCancelledIsOnlyMarked() {
        departures.plant(new DepartureState(SALIDA, TENANT, SERVICE, AT.plusSeconds(86_400), DepartureStatus.FINALIZED, 10, 0, AT));
        reservations(2);
        UUID eventId = UUID.randomUUID();

        assertThat(useCase.handle(event(eventId))).isEqualTo(CancelledDepartureOutcome.DONE);

        assertThat(attempts).isEmpty();
        assertThat(departures.processed).containsExactly(eventId);
    }

    @Test
    void opensEpisodesWithAnInactiveTenant() {
        // The use case has no tenant status among its dependencies: the episodes open and the clocks follow spec 018 (I1).
        reservations(1);

        assertThat(useCase.handle(event(UUID.randomUUID()))).isEqualTo(CancelledDepartureOutcome.DONE);
        assertThat(episodes).hasSize(1);
    }
}
