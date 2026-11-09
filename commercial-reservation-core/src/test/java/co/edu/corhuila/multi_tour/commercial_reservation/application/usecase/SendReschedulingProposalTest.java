package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SendProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalState;
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

// T-03 of HU-COM-018 (-api): RN-02, RN-03; AC-18.3, AC-18.4, AC-18.9 (plan D-04). Sending a proposal checks, in this order, the role, the proof (none in validation), the service of the
// proposed departure, the period still open and the seats; only then it holds the seats of every traveller in the proposed departure and stores the proposal. Any refusal changes nothing.
// The persistence adapters and the route are later pull requests; the ports have doubles here.
class SendReschedulingProposalTest {

    private static final UUID TENANT = new UUID(18, 21), ADMIN = new UUID(18, 22), CUSTOMER = new UUID(18, 23), SERVICE = new UUID(18, 24), ORIGINAL = new UUID(18, 25),
            PROPOSED = new UUID(18, 26), RESERVATION = new UUID(18, 27), EPISODE = new UUID(18, 28), OTHER_SERVICE = new UUID(18, 29);
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final Map<UUID, ReschedulingEpisode> stored = new LinkedHashMap<>();
    private final List<String> holds = new ArrayList<>();
    private final ReschedulingRepository episodes = new ReschedulingRepository() {
        @Override public Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID originalSalidaId) { return Optional.empty(); }
        @Override public Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId) { return Optional.ofNullable(stored.get(episodeId)).filter(e -> e.tenantId().equals(tenantId)); }
        @Override public void save(ReschedulingEpisode episode) { stored.put(episode.id(), episode); }
    };
    private final SalidaCapacityHolder holder = new SalidaCapacityHolder() {
        @Override public boolean hold(UUID tenantId, UUID salidaId, int requested) { return fakes.hold(tenantId, salidaId, requested); }
        @Override public int release(UUID tenantId, UUID reservationId) { return fakes.release(tenantId, reservationId); }
        @Override public boolean holdForRescheduling(UUID tenantId, UUID reservationId, UUID salidaId, int seats) {
            boolean held = fakes.hold(tenantId, salidaId, seats);
            if (held) holds.add(reservationId + "/" + salidaId + "/" + seats);
            return held;
        }
    };
    private final SendReschedulingProposalUseCase send = new SendReschedulingProposalUseCase(episodes, fakes, departures, holder, fakes, () -> new UUID(18, 100),
            Clock.fixed(NOW, ZoneOffset.UTC));

    SendReschedulingProposalTest() {
        departures.plant(new DepartureState(ORIGINAL, TENANT, SERVICE, NOW.plus(Duration.ofDays(30)), DepartureStatus.CANCELLED, 10, 0));
        departures.plant(new DepartureState(PROPOSED, TENANT, SERVICE, NOW.plus(Duration.ofDays(40)), DepartureStatus.SCHEDULED, 10, 7));
        departures.plant(new DepartureState(new UUID(18, 30), TENANT, OTHER_SERVICE, NOW.plus(Duration.ofDays(40)), DepartureStatus.SCHEDULED, 10, 0));
        fakes.reservations.put(RESERVATION, Reservation.create(RESERVATION, TENANT, CUSTOMER, ORIGINAL, ADMIN, 3, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(300_000), new Cents(300_000), List.of(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofDays(2))));
        stored.put(EPISODE, ReschedulingEpisode.open(EPISODE, TENANT, RESERVATION, ORIGINAL, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(2))));
    }

    private SendProposalCommand command(UUID salida) {
        return new SendProposalCommand(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR, EPISODE, salida);
    }

    @Test
    void sendingAProposalHoldsTheSeatsInTheProposedDepartureAndKeepsTheOriginalUntouched() {
        ReschedulingEpisode episode = send.send(command(PROPOSED));

        assertThat(episode.pendingProposal()).hasValueSatisfying(p -> {
            assertThat(p.salidaId()).isEqualTo(PROPOSED);
            assertThat(p.partySize()).isEqualTo(3);
            assertThat(p.state()).isEqualTo(ProposalState.PENDING);
            assertThat(p.proposedAt()).isEqualTo(NOW);
            assertThat(p.responseDeadlineAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));
        });
        assertThat(holds).containsExactly(RESERVATION + "/" + PROPOSED + "/3");
        assertThat(departures.rows.get(PROPOSED).heldSeats()).isEqualTo(10);
        assertThat(stored.get(EPISODE)).isEqualTo(episode);
        assertThat(fakes.reservations.get(RESERVATION).departureId()).as("the reservation keeps its date until the customer accepts").isEqualTo(ORIGINAL);
    }

    @Test
    void aProposalNeedsSeatsForEveryTravellerOrChangesNothing() {
        departures.plant(new DepartureState(PROPOSED, TENANT, SERVICE, NOW.plus(Duration.ofDays(40)), DepartureStatus.SCHEDULED, 10, 8));

        assertThatThrownBy(() -> send.send(command(PROPOSED))).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("seats");
        assertThat(departures.rows.get(PROPOSED).heldSeats()).isEqualTo(8);
        assertThat(stored.get(EPISODE).proposals()).isEmpty();
        assertThat(holds).isEmpty();
    }

    @Test
    void aProofInValidationRefusesTheProposal() {
        fakes.reservations.get(RESERVATION).registerTransfer(new UUID(18, 40), new Cents(100_000), new Cents(100_000), ADMIN, NOW.minus(Duration.ofHours(1)));

        assertThatThrownBy(() -> send.send(command(PROPOSED))).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("proof");
        assertThat(stored.get(EPISODE).proposals()).isEmpty();
        assertThat(departures.rows.get(PROPOSED).heldSeats()).isEqualTo(7);
    }

    @Test
    void aDepartureOfAnotherServiceOrNotScheduledIsRefused() {
        assertThatThrownBy(() -> send.send(command(new UUID(18, 30)))).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> send.send(command(ORIGINAL))).isInstanceOf(BusinessRuleViolation.class);
        assertThat(stored.get(EPISODE).proposals()).isEmpty();
    }

    @Test
    void onlyTheAdministratorAndTheCollaboratorProposeAndOnlyInsideTheirTenant() {
        assertThatThrownBy(() -> send.send(new SendProposalCommand(TENANT, ADMIN, Role.MANAGER, EPISODE, PROPOSED))).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> send.send(new SendProposalCommand(TENANT, CUSTOMER, Role.FINAL_CUSTOMER, EPISODE, PROPOSED))).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> send.send(new SendProposalCommand(new UUID(18, 99), ADMIN, Role.TENANT_ADMINISTRATOR, EPISODE, PROPOSED))).isInstanceOf(ReschedulingNotFoundException.class);
        assertThat(holds).isEmpty();
    }
}
