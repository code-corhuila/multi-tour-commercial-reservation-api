package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OpenReschedulingCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DeadlineFreezeCause;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EconomicDeadlineFreezer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-02 of HU-COM-018 (-api): RN-07; AC-18.8 (plan D-06). The saga opens one episode per reservation and incident: the mark is set, the seat on the cancelled departure is
// released, the economic deadlines are frozen and the 24 hours count from the cancellation instant sent by the saga, not from the call. A repeat changes nothing. The
// persistence adapters and the internal route are later pull requests; the ports have doubles here.
class OpenReschedulingTest {

    private static final UUID TENANT = new UUID(18, 1), ADMIN = new UUID(18, 2), CUSTOMER = new UUID(18, 3), SERVICE = new UUID(18, 4), DEPARTURE = new UUID(18, 5),
            RESERVATION = new UUID(18, 6), EPISODE = new UUID(18, 7);
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant CANCELLED_AT = NOW.minus(Duration.ofMinutes(20));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final Map<UUID, ReschedulingEpisode> stored = new LinkedHashMap<>();
    private final List<String> frozen = new ArrayList<>();
    private final ReschedulingRepository episodes = new ReschedulingRepository() {
        @Override public Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID originalSalidaId) {
            return stored.values().stream().filter(e -> e.tenantId().equals(tenantId) && e.reservationId().equals(reservationId) && e.originalSalidaId().equals(originalSalidaId)).findFirst();
        }
        @Override public Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId) { return Optional.ofNullable(stored.get(episodeId)).filter(e -> e.tenantId().equals(tenantId)); }
        @Override public void save(ReschedulingEpisode episode) { stored.put(episode.id(), episode); }
    };
    private final EconomicDeadlineFreezer freezer = (tenantId, reservationId, at, cause) -> frozen.add(reservationId + "@" + at + "/" + cause);
    private final OpenReschedulingUseCase open = new OpenReschedulingUseCase(episodes, fakes, fakes, freezer, fakes, fakes, () -> EPISODE, Clock.fixed(NOW, ZoneOffset.UTC));

    OpenReschedulingTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plus(Duration.ofDays(30)), DepartureStatus.CANCELLED, 10, 2));
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(300_000), new Cents(300_000), List.of(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofDays(2)));
        reservation.allocateSeat(NOW.minus(Duration.ofDays(2)));
        fakes.reservations.put(RESERVATION, reservation);
    }

    private OpenReschedulingCommand command() {
        return new OpenReschedulingCommand(TENANT, RESERVATION, DEPARTURE, CANCELLED_AT);
    }

    @Test
    void openingTheEpisodeFreezesTheEconomicDeadlinesAndReleasesTheCancelledSeat() {
        ReschedulingEpisode episode = open.open(command());

        assertThat(episode.id()).isEqualTo(EPISODE);
        assertThat(episode.state()).isEqualTo(EpisodeState.OPEN);
        assertThat(episode.cancelledAt()).as("the 24 hours count from the cancellation sent by the saga").isEqualTo(CANCELLED_AT);
        assertThat(episode.proposalPeriodEnd()).isEqualTo(CANCELLED_AT.plus(Duration.ofHours(24)));
        assertThat(episode.openedAt()).isEqualTo(NOW);
        assertThat(stored).containsOnlyKeys(EPISODE);
        assertThat(frozen).containsExactly(RESERVATION + "@" + NOW + "/" + DeadlineFreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED);
        assertThat(fakes.releaseCalls).isEqualTo(1);
        assertThat(departures.rows.get(DEPARTURE).heldSeats()).isZero();
        assertThat(fakes.reservations.get(RESERVATION).seatHeld()).isFalse();
        assertThat(fakes.events).as("the release of the seat is in the outbox, once, in the same transaction").filteredOn(e -> e instanceof co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityReleased).hasSize(1);
    }

    @Test
    void openingTwiceForTheSameIncidentLeavesOneEpisode() {
        ReschedulingEpisode first = open.open(command());
        ReschedulingEpisode second = open.open(command());

        assertThat(second).isEqualTo(first);
        assertThat(stored).hasSize(1);
        assertThat(frozen).hasSize(1);
        assertThat(fakes.releaseCalls).isEqualTo(1);
    }

    @Test
    void anEpisodeIsOpenedOnlyForAReservationOfTheTenantOnTheDepartureThatWasCancelled() {
        assertThatThrownBy(() -> open.open(new OpenReschedulingCommand(new UUID(18, 99), RESERVATION, DEPARTURE, CANCELLED_AT))).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> open.open(new OpenReschedulingCommand(TENANT, RESERVATION, new UUID(18, 98), CANCELLED_AT))).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> open.open(new OpenReschedulingCommand(TENANT, RESERVATION, DEPARTURE, NOW.plusSeconds(60)))).isInstanceOf(BusinessRuleViolation.class);
        assertThat(stored).isEmpty();
        assertThat(frozen).isEmpty();
    }
}
