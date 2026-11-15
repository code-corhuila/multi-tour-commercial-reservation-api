package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ClosingReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
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

// T-05 of HU-COM-018 (-api): RN-08, RN-09; AC-18.12, AC-18.13. When the proposed departure is cancelled, every pending proposal for it is void and its seats go back at once. With the tenant
// active a new period of the lowest of 24 hours and the episode remainder opens and the reservation stays; with the tenant inactive (I1) the reservation is cancelled at once with a full refund of
// the validated values and no new period. Another departure is never touched. The persistence adapter and the route are later pull requests.
class VoidReschedulingProposalsTest {

    private static final UUID TENANT = new UUID(18, 81), ADMIN = new UUID(18, 82), CUSTOMER = new UUID(18, 83), SERVICE = new UUID(18, 84), ORIGINAL = new UUID(18, 85),
            PROPOSED = new UUID(18, 86), OTHER = new UUID(18, 87), RESERVATION = new UUID(18, 88), EPISODE = new UUID(18, 89), RESERVATION_2 = new UUID(18, 90), EPISODE_2 = new UUID(18, 91);
    private static final Instant CANCELLED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant NOW = CANCELLED_AT.plus(Duration.ofHours(30));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final Map<UUID, ReschedulingEpisode> stored = new LinkedHashMap<>();
    private final List<String> journal = new ArrayList<>();
    private Optional<TenantStatus> status = Optional.of(TenantStatus.ACTIVE);
    private final ReschedulingRepository episodes = new ReschedulingRepository() {
        @Override public Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID originalSalidaId) { return Optional.empty(); }
        @Override public Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId) { return Optional.ofNullable(stored.get(episodeId)).filter(e -> e.tenantId().equals(tenantId)); }
        @Override public void save(ReschedulingEpisode episode) { stored.put(episode.id(), episode); }
        @Override public List<ReschedulingEpisode> findPendingOn(UUID tenantId, UUID proposedSalidaId) {
            return stored.values().stream().filter(e -> e.tenantId().equals(tenantId)).filter(e -> e.pendingProposal().filter(p -> p.salidaId().equals(proposedSalidaId)).isPresent()).toList();
        }
    };
    private final SalidaCapacityHolder holder = new SalidaCapacityHolder() {
        @Override public boolean hold(UUID tenantId, UUID salidaId, int requested) { return fakes.hold(tenantId, salidaId, requested); }
        @Override public int release(UUID tenantId, UUID reservationId) { return fakes.release(tenantId, reservationId); }
        @Override public int releaseReschedulingHold(UUID tenantId, UUID reservationId) { journal.add("hold-released:" + reservationId); return 3; }
    };
    private final DetermineRefund refunds = (tenantId, reservationId, origin, amountCents) -> { journal.add("refund:" + reservationId); return null; };
    private final VoidReschedulingProposalsUseCase voiding = new VoidReschedulingProposalsUseCase(episodes, fakes, holder, refunds, fakes, tenantId -> status, fakes,
            Clock.fixed(NOW, ZoneOffset.UTC));

    VoidReschedulingProposalsTest() {
        reservation(RESERVATION, CUSTOMER);
        reservation(RESERVATION_2, CUSTOMER);
        stored.put(EPISODE, episodeWithProposalOn(EPISODE, RESERVATION, PROPOSED));
        stored.put(EPISODE_2, episodeWithProposalOn(EPISODE_2, RESERVATION_2, OTHER));
    }

    private void reservation(UUID id, UUID customer) {
        Reservation reservation = Reservation.create(id, TENANT, customer, ORIGINAL, ADMIN, 3, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(450_000), new Cents(450_000), List.of(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, CANCELLED_AT.minus(Duration.ofDays(2)));
        reservation.restoredAs(ReservationStatus.CONFIRMED, PaymentStatus.DEPOSIT_RECEIVED, new Cents(300_000), false, List.of());
        fakes.reservations.put(id, reservation);
    }

    private static ReschedulingEpisode episodeWithProposalOn(UUID id, UUID reservation, UUID salida) {
        return ReschedulingEpisode.open(id, TENANT, reservation, ORIGINAL, CANCELLED_AT, CANCELLED_AT)
                .propose(new UUID(18, 200 + id.getLeastSignificantBits()), salida, 3, ADMIN, CANCELLED_AT.plus(Duration.ofHours(10)), NOW.plus(Duration.ofDays(40)));
    }

    @Test
    void aVoidedProposalFreesItsSeatsAndOpensAPeriodOfTheLowestOf24hAndTheRemainder() {
        List<UUID> voided = voiding.voidProposals(TENANT, PROPOSED, NOW);

        assertThat(voided).containsExactly(EPISODE);
        ReschedulingEpisode episode = stored.get(EPISODE);
        assertThat(episode.state()).isEqualTo(EpisodeState.OPEN);
        assertThat(episode.proposals().get(0).state()).as("void, not rejected").isEqualTo(ProposalState.VOID);
        assertThat(episode.proposalPeriodEnd()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(journal).containsExactly("hold-released:" + RESERVATION);
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void aProposedDepartureCancelledWithTheTenantInactiveCancelsAtOnceWithNoNewPeriod() {
        status = Optional.of(TenantStatus.INACTIVE);

        voiding.voidProposals(TENANT, PROPOSED, NOW);

        ReschedulingEpisode episode = stored.get(EPISODE);
        assertThat(episode.state()).isEqualTo(EpisodeState.CANCELLED);
        assertThat(episode.reason()).isEqualTo(ClosingReason.PROPOSED_DEPARTURE_CANCELLED_WHILE_INACTIVE);
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(fakes.reservations.get(RESERVATION).cancellationCause()).isEqualTo(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER);
        assertThat(journal).containsExactly("hold-released:" + RESERVATION, "refund:" + RESERVATION);
    }

    @Test
    void anotherDepartureOrAnotherTenantIsNeverTouchedAndARepeatChangesNothing() {
        assertThat(voiding.voidProposals(new UUID(18, 99), PROPOSED, NOW)).isEmpty();
        assertThat(voiding.voidProposals(TENANT, new UUID(18, 98), NOW)).isEmpty();
        assertThat(stored.get(EPISODE_2).pendingProposal()).isPresent();

        voiding.voidProposals(TENANT, PROPOSED, NOW);
        journal.clear();
        assertThat(voiding.voidProposals(TENANT, PROPOSED, NOW)).as("the proposal is no longer pending").isEmpty();
        assertThat(journal).isEmpty();
        assertThat(stored.get(EPISODE_2).pendingProposal()).isPresent();
    }
}
