package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExtraordinaryOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OpenReschedulingCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.OpenRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExtraordinaryCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// Spec 025 (-api), T-01 (AC-25.1 to AC-25.4, AC-25.6, AC-25.9): the extraordinary cancellation of one reservation in execution opens the episode of rescheduling, the decision of
// RN-EJE-002. The reservation is never cancelled by it and no refund exists yet. The episode is the one of spec 018: its end in a full refund when no alternative is accepted is
// tested by ConcludeReschedulingTest. Doubles only; no tenant status is read, so an inactive tenant opens it too.
class ExtraordinaryCancellationUseCaseTest {

    private static final UUID TENANT = new UUID(25, 1), SALIDA = new UUID(25, 2), RESERVATION = new UUID(25, 3), SERVICE = new UUID(25, 4);
    private static final Instant AT = Instant.parse("2026-11-12T15:00:00Z");

    private final Map<UUID, Reservation> reservations = new LinkedHashMap<>();
    private final Map<UUID, ReschedulingEpisode> episodes = new LinkedHashMap<>();
    private final Set<UUID> processed = new HashSet<>();
    private final List<OpenReschedulingCommand> opened = new ArrayList<>();
    private RuntimeException openFailure;
    private int depth;

    private final PaymentTransaction transaction = new PaymentTransaction() {
        @Override public <T> T inTransaction(Supplier<T> work) {
            depth++;
            try { return work.get(); } finally { depth--; }
        }
    };
    private final ProcessedEvents marks = new ProcessedEvents() {
        @Override public boolean wasProcessed(UUID eventId) { return processed.contains(eventId); }
        @Override public void record(UUID eventId) {
            if (depth == 0) throw new IllegalStateException("the mark was written outside the transaction");
            processed.add(eventId);
        }
    };
    private final ReservationRepository repository = new ReservationRepository() {
        @Override public void save(Reservation reservation) { throw new AssertionError("the extraordinary cancellation never changes the reservation itself"); }
        @Override public Optional<Reservation> findByTenantAndId(UUID tenantId, UUID reservationId) {
            return Optional.ofNullable(reservations.get(reservationId)).filter(r -> r.tenantId().equals(tenantId));
        }
    };
    private final ReschedulingRepository episodeRepository = new ReschedulingRepository() {
        @Override public Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID salidaId) {
            return episodes.values().stream().filter(e -> e.reservationId().equals(reservationId) && e.originalSalidaId().equals(salidaId)).findFirst();
        }
        @Override public Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId) { return Optional.ofNullable(episodes.get(episodeId)); }
        @Override public void save(ReschedulingEpisode episode) { episodes.put(episode.id(), episode); }
    };
    private final OpenRescheduling opening = command -> {
        if (depth == 0) throw new IllegalStateException("the episode was opened outside the transaction");
        if (openFailure != null) throw openFailure;
        opened.add(command);
        ReschedulingEpisode episode = ReschedulingEpisode.open(UUID.randomUUID(), command.tenantId(), command.reservationId(), command.departureId(), command.cancelledAt(), AT);
        episodes.put(episode.id(), episode);
        return episode;
    };
    private final ExtraordinaryCancellationUseCase useCase = new ExtraordinaryCancellationUseCase(marks, repository, episodeRepository, opening, transaction);

    ExtraordinaryCancellationUseCaseTest() {
        plant(ReservationStatus.IN_EXECUTION);
    }

    private void plant(ReservationStatus status) {
        Reservation reservation = Reservation.create(RESERVATION, TENANT, UUID.randomUUID(), SALIDA, UUID.randomUUID(), 3,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(450_000), new Cents(450_000), List.of(), PaymentType.FULL_PAYMENT,
                PaymentMethod.TRANSFER, AT.minus(Duration.ofDays(30)));
        reservation.restoredAs(status, PaymentStatus.FULLY_PAID, new Cents(0), false, List.of());
        reservations.put(RESERVATION, reservation);
    }

    private ExtraordinaryCancellation event(UUID eventId) {
        return new ExtraordinaryCancellation(eventId, TENANT, SALIDA, RESERVATION, AT);
    }

    @Test
    void opensAnEpisodeAndKeepsTheReservationAlive() {
        UUID eventId = UUID.randomUUID();

        assertThat(useCase.apply(event(eventId))).isEqualTo(ExtraordinaryOutcome.EPISODE_OPENED);

        assertThat(opened).containsExactly(new OpenReschedulingCommand(TENANT, RESERVATION, SALIDA, AT));
        assertThat(processed).containsExactly(eventId);
        assertThat(reservations.get(RESERVATION).reservationStatus()).as("the decision comes first: nothing is cancelled").isEqualTo(ReservationStatus.IN_EXECUTION);
    }

    @Test
    void createsNoRefundWhenTheEpisodeOpens() {
        useCase.apply(event(UUID.randomUUID()));

        // the use case has no refund dependency at all: the 100 percent refund is only the end of an episode without an alternative (spec 018)
        assertThat(ExtraordinaryCancellationUseCase.class.getDeclaredConstructors()[0].getParameterTypes())
                .noneMatch(type -> type.getSimpleName().contains("Refund"));
    }

    @Test
    void countsTheDeadlinesFromTheEventInstant() {
        useCase.apply(event(UUID.randomUUID()));

        assertThat(opened.get(0).cancelledAt()).isEqualTo(AT);
    }

    @Test
    void repeatsTheEventWithoutASecondEpisode() {
        UUID eventId = UUID.randomUUID();
        useCase.apply(event(eventId));

        assertThat(useCase.apply(event(eventId))).isEqualTo(ExtraordinaryOutcome.DUPLICATE);
        UUID second = UUID.randomUUID();
        assertThat(useCase.apply(event(second))).isEqualTo(ExtraordinaryOutcome.ALREADY_OPEN);

        assertThat(opened).hasSize(1);
        assertThat(episodes).hasSize(1);
        assertThat(processed).contains(second);
    }

    @Test
    void ignoresACancelledOrCompletedReservation() {
        reservations.get(RESERVATION).cancel(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER, null, AT, 0);
        UUID cancelled = UUID.randomUUID();

        assertThat(useCase.apply(event(cancelled))).isEqualTo(ExtraordinaryOutcome.IGNORED_FINAL);

        reservations.clear();
        plant(ReservationStatus.COMPLETED);
        UUID completed = UUID.randomUUID();
        assertThat(useCase.apply(event(completed))).isEqualTo(ExtraordinaryOutcome.IGNORED_FINAL);

        assertThat(opened).isEmpty();
        assertThat(processed).containsExactlyInAnyOrder(cancelled, completed);
    }

    @Test
    void refusesAReservationOfAnotherDepartureOrAnUnknownOneAndLeavesTheEventUnmarked() {
        ExtraordinaryCancellation otherDeparture = new ExtraordinaryCancellation(UUID.randomUUID(), TENANT, new UUID(25, 99), RESERVATION, AT);
        assertThat(useCase.apply(otherDeparture)).isEqualTo(ExtraordinaryOutcome.UNKNOWN_RESERVATION);

        reservations.clear();
        assertThat(useCase.apply(event(UUID.randomUUID()))).isEqualTo(ExtraordinaryOutcome.UNKNOWN_RESERVATION);

        assertThat(processed).isEmpty();
        assertThat(opened).isEmpty();
    }

    @Test
    void anInstantInTheFutureIsRefusedAndLeavesTheEventUnmarked() {
        openFailure = new BusinessRuleViolation("the cancellation cannot be in the future");
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.apply(event(eventId))).isInstanceOf(DomainException.class).extracting("field").isEqualTo("occurredAt");

        assertThat(processed).isEmpty();
    }

    @Test
    void opensTheEpisodeForAnInactiveTenant() {
        // No tenant status is a dependency of the use case: the clocks follow spec 018 (I1).
        assertThat(useCase.apply(event(UUID.randomUUID()))).isEqualTo(ExtraordinaryOutcome.EPISODE_OPENED);
    }
}
