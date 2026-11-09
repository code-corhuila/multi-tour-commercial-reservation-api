package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AnswerProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ClosingReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationCancelled;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
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

// T-05 of HU-COM-018 (-api): RN-06, RN-09; AC-18.7, AC-18.10, AC-18.11 (plan D-05, D-06). Not accepted means cancelled with 100 % of the validated values and no penalty: the reservation takes the cause
// NOT_ATTRIBUTABLE_TO_CUSTOMER through its own cancellation, the seats and the hold are released, and a refund determination of origin NOT_ATTRIBUTABLE_TO_CUSTOMER is left only when there are
// validated values. The rejection, the declaration of no alternative and the expiry commit with the episode; an expiry applies one outcome or answers that nothing is due. The voiding of a proposal
// and the inactive-tenant cases are the next slices; the persistence adapters and the routes are later pull requests.
class ConcludeReschedulingTest {

    private static final UUID TENANT = new UUID(18, 71), ADMIN = new UUID(18, 72), CUSTOMER = new UUID(18, 73), SERVICE = new UUID(18, 74), ORIGINAL = new UUID(18, 75),
            PROPOSED = new UUID(18, 76), RESERVATION = new UUID(18, 77), EPISODE = new UUID(18, 78);
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-11-10T09:00:00Z");

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final Map<UUID, ReschedulingEpisode> stored = new LinkedHashMap<>();
    private final List<String> journal = new ArrayList<>();
    private final ReschedulingRepository episodes = new ReschedulingRepository() {
        @Override public Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID originalSalidaId) { return Optional.empty(); }
        @Override public Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId) { return Optional.ofNullable(stored.get(episodeId)).filter(e -> e.tenantId().equals(tenantId)); }
        @Override public void save(ReschedulingEpisode episode) { stored.put(episode.id(), episode); }
    };
    private final SalidaCapacityHolder holder = new SalidaCapacityHolder() {
        @Override public boolean hold(UUID tenantId, UUID salidaId, int requested) { return fakes.hold(tenantId, salidaId, requested); }
        @Override public int release(UUID tenantId, UUID reservationId) { return fakes.release(tenantId, reservationId); }
        @Override public int releaseReschedulingHold(UUID tenantId, UUID reservationId) { journal.add("hold-released:" + reservationId); return 3; }
    };
    private final DetermineRefund refunds = (tenantId, reservationId, origin, amountCents) -> { journal.add("refund:" + reservationId + "/" + origin + "/" + amountCents); return null; };
    private final MutableClock clock = new MutableClock(NOW);
    private ConcludeReschedulingUseCase conclude = new ConcludeReschedulingUseCase(episodes, fakes, holder, refunds, fakes, fakes, clock);

    ConcludeReschedulingTest() {
        departures.plant(new DepartureState(ORIGINAL, TENANT, SERVICE, NOW.plus(Duration.ofDays(30)), DepartureStatus.CANCELLED, 10, 0));
        departures.plant(new DepartureState(PROPOSED, TENANT, SERVICE, NOW.plus(Duration.ofDays(40)), DepartureStatus.SCHEDULED, 10, 3));
        reservation(ReservationStatus.CONFIRMED, new Cents(300_000));
        stored.put(EPISODE, ReschedulingEpisode.open(EPISODE, TENANT, RESERVATION, ORIGINAL, CANCELLED_AT, CANCELLED_AT));
    }

    private void reservation(ReservationStatus status, Cents pending) {
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, ORIGINAL, ADMIN, 3, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(450_000), new Cents(450_000), List.of(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, CANCELLED_AT.minus(Duration.ofDays(2)));
        reservation.restoredAs(status, pending.amount() == 450_000 ? PaymentStatus.NO_PAYMENT : PaymentStatus.DEPOSIT_RECEIVED, pending, false, List.of());
        fakes.reservations.put(RESERVATION, reservation);
    }

    private void withPendingProposal(Instant proposedAt) {
        stored.put(EPISODE, stored.get(EPISODE).propose(new UUID(18, 79), PROPOSED, 3, ADMIN, proposedAt, NOW.plus(Duration.ofDays(40))));
    }

    @Test
    void rejectingCancelsWithTheNotAttributableCauseAndReleasesTheSeats() {
        withPendingProposal(NOW.minus(Duration.ofHours(30)));

        ReschedulingEpisode closed = conclude.reject(AnswerProposalCommand.customer(TENANT, CUSTOMER, CUSTOMER, EPISODE, false));

        Reservation reservation = fakes.reservations.get(RESERVATION);
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.cancellationCause()).isEqualTo(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER);
        assertThat(closed.state()).isEqualTo(EpisodeState.CANCELLED);
        assertThat(closed.reason()).isEqualTo(ClosingReason.REJECTED_BY_CUSTOMER);
        assertThat(closed.proposals().get(0).state()).isEqualTo(ProposalState.REJECTED);
        assertThat(journal).containsExactly("hold-released:" + RESERVATION, "refund:" + RESERVATION + "/" + RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER + "/null");
        assertThat(fakes.events).filteredOn(e -> e instanceof ReservationCancelled).singleElement().satisfies(e -> assertThat(((ReservationCancelled) e).cause())
                .isEqualTo(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER));
        assertThat(stored.get(EPISODE)).isEqualTo(closed);
    }

    @Test
    void aRefundDeterminationIsLeftOnlyWhenThereAreValidatedValues() {
        reservation(ReservationStatus.PENDING_PAYMENT, new Cents(450_000));

        conclude.declareNoAlternative(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR, EPISODE);

        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(journal).as("nothing was paid, so nothing is determined").noneMatch(line -> line.startsWith("refund:"));
    }

    @Test
    void noAlternativeIsAcceptedOnlyBeforeAProposalExists() {
        withPendingProposal(CANCELLED_AT.plus(Duration.ofHours(2)));

        assertThatThrownBy(() -> conclude.declareNoAlternative(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR, EPISODE)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        stored.put(EPISODE, ReschedulingEpisode.open(EPISODE, TENANT, RESERVATION, ORIGINAL, CANCELLED_AT, CANCELLED_AT));
        assertThatThrownBy(() -> conclude.declareNoAlternative(TENANT, CUSTOMER, Role.FINAL_CUSTOMER, EPISODE)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThat(conclude.declareNoAlternative(TENANT, ADMIN, Role.OPERATIONAL_COLLABORATOR, EPISODE).reason()).isEqualTo(ClosingReason.NO_ALTERNATIVE);
        assertThat(fakes.reservations.get(RESERVATION).cancellationCause()).isEqualTo(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER);
    }

    @Test
    void theLapseOfTheFirstTwentyFourHoursCancelsWithAFullRefund() {
        ReschedulingEpisode closed = conclude.expire(TENANT, EPISODE);

        assertThat(closed.reason()).isEqualTo(ClosingReason.PROPOSAL_PERIOD_EXPIRED);
        assertThat(fakes.reservations.get(RESERVATION).cancellationCause()).isEqualTo(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER);
        assertThat(journal).contains("refund:" + RESERVATION + "/" + RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER + "/null");
    }

    @Test
    void theResponseDeadlineAndTheEpisodeLimitCancelWithAFullRefundAndReleaseEveryHold() {
        clock.now = CANCELLED_AT.plus(Duration.ofHours(97));
        withPendingProposal(CANCELLED_AT.plus(Duration.ofHours(10)));

        ReschedulingEpisode closed = conclude.expire(TENANT, EPISODE);

        assertThat(closed.reason()).isEqualTo(ClosingReason.EPISODE_LIMIT_REACHED);
        assertThat(closed.proposals().get(0).state()).isEqualTo(ProposalState.EXPIRED);
        assertThat(journal).containsExactly("hold-released:" + RESERVATION, "refund:" + RESERVATION + "/" + RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER + "/null");
    }

    @Test
    void anExpiryWithNothingDueChangesNothingAndARepeatAnswersTheStoredEpisode() {
        clock.now = CANCELLED_AT.plus(Duration.ofHours(5));

        assertThatThrownBy(() -> conclude.expire(TENANT, EPISODE)).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("nothing is due");
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(journal).isEmpty();

        clock.now = NOW;
        conclude.expire(TENANT, EPISODE);
        assertThat(conclude.expire(TENANT, EPISODE).reason()).as("a repeat answers the stored episode").isEqualTo(ClosingReason.PROPOSAL_PERIOD_EXPIRED);
    }

    @Test
    void aRepeatOfTheExpiryOrOfNoAlternativeAnswersTheStoredEpisodeAndChangesNothing() {
        ReschedulingEpisode first = conclude.expire(TENANT, EPISODE);
        journal.clear();

        ReschedulingEpisode again = conclude.expire(TENANT, EPISODE);

        assertThat(again).isEqualTo(first);
        assertThat(journal).as("no second refund and no second release").isEmpty();

        stored.put(EPISODE, ReschedulingEpisode.open(EPISODE, TENANT, RESERVATION, ORIGINAL, CANCELLED_AT, CANCELLED_AT));
        reservation(ReservationStatus.CONFIRMED, new Cents(300_000));
        ReschedulingEpisode none = conclude.declareNoAlternative(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR, EPISODE);
        journal.clear();
        assertThat(conclude.declareNoAlternative(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR, EPISODE)).isEqualTo(none);
        assertThat(journal).isEmpty();
    }

    @Test
    void aReservationInExecutionIsCancelledToo() {
        reservation(ReservationStatus.IN_EXECUTION, new Cents(300_000));

        conclude.expire(TENANT, EPISODE);

        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    private static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) { this.now = now; }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }
}
