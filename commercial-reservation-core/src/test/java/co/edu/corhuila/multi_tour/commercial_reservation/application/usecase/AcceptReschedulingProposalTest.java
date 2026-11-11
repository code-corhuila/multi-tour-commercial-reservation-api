package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AnswerProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EconomicDeadlineResumer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalAnswer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationRescheduled;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingRecord;
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

// T-04 of HU-COM-018 (-api), acceptance half: RN-01, RN-05; AC-18.1, AC-18.6, AC-18.14 (plan D-04, D-05). Accepting takes the same reservation to the proposed departure with its payments and history,
// turns the hold into the reservation's seat (the old one was released at the opening), returns an IN_EXECUTION reservation to CONFIRMED, resumes the frozen economic deadlines capped by the
// new start, and keeps a record and one event with no personal data; all together. The customer answers only their own proposal; an outside answer is recorded by the Administrator or the
// Collaborator and takes effect at once. The reject path and the lapses are the next pull request.
class AcceptReschedulingProposalTest {

    private static final UUID TENANT = new UUID(18, 51), ADMIN = new UUID(18, 52), CUSTOMER = new UUID(18, 53), OTHER = new UUID(18, 54), SERVICE = new UUID(18, 55), ORIGINAL = new UUID(18, 56),
            PROPOSED = new UUID(18, 57), RESERVATION = new UUID(18, 58), EPISODE = new UUID(18, 59);
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant NEW_START = NOW.plus(Duration.ofDays(40));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final Map<UUID, ReschedulingEpisode> stored = new LinkedHashMap<>();
    private final List<ReschedulingRecord> records = new ArrayList<>();
    private final List<String> journal = new ArrayList<>();
    private final ReschedulingRepository episodes = new ReschedulingRepository() {
        @Override public Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID originalSalidaId) { return Optional.empty(); }
        @Override public Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId) { return Optional.ofNullable(stored.get(episodeId)).filter(e -> e.tenantId().equals(tenantId)); }
        @Override public void save(ReschedulingEpisode episode) { stored.put(episode.id(), episode); }
    };
    private final SalidaCapacityHolder holder = new SalidaCapacityHolder() {
        @Override public boolean hold(UUID tenantId, UUID salidaId, int requested) { return fakes.hold(tenantId, salidaId, requested); }
        @Override public int release(UUID tenantId, UUID reservationId) { return fakes.release(tenantId, reservationId); }
        @Override public void acceptReschedulingHold(UUID tenantId, UUID reservationId, UUID salidaId) { journal.add("seat:" + reservationId + "/" + salidaId); }
    };
    private final EconomicDeadlineResumer resumer = (tenantId, reservationId, at, departureStart) -> journal.add("resume:" + reservationId + "@" + at + "<=" + departureStart);
    private final ReschedulingRecords recordStore = records::add;
    private final AcceptReschedulingProposalUseCase answer = new AcceptReschedulingProposalUseCase(episodes, fakes, departures, holder, resumer, recordStore, fakes, fakes,
            () -> new UUID(18, 100), Clock.fixed(NOW, ZoneOffset.UTC));

    AcceptReschedulingProposalTest() {
        departures.plant(new DepartureState(ORIGINAL, TENANT, SERVICE, NOW.plus(Duration.ofDays(30)), DepartureStatus.CANCELLED, 10, 0));
        departures.plant(new DepartureState(PROPOSED, TENANT, SERVICE, NEW_START, DepartureStatus.SCHEDULED, 10, 3));
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, ORIGINAL, ADMIN, 3, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(450_000), new Cents(450_000), List.of(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofDays(2)));
        reservation.restoredAs(ReservationStatus.IN_EXECUTION, PaymentStatus.DEPOSIT_RECEIVED, new Cents(300_000), false, List.of());
        fakes.reservations.put(RESERVATION, reservation);
        stored.put(EPISODE, ReschedulingEpisode.open(EPISODE, TENANT, RESERVATION, ORIGINAL, NOW.minus(Duration.ofHours(5)), NOW.minus(Duration.ofHours(5)))
                .propose(new UUID(18, 60), PROPOSED, 3, ADMIN, NOW.minus(Duration.ofHours(3)), NEW_START));
    }

    private AnswerProposalCommand byCustomer(boolean accept) {
        return AnswerProposalCommand.customer(TENANT, CUSTOMER, CUSTOMER, EPISODE, accept);
    }

    @Test
    void acceptingKeepsTheSameReservationItsPaymentsAndHistory() {
        Reservation before = fakes.reservations.get(RESERVATION);
        Cents pending = before.pendingBalance();

        ReschedulingEpisode closed = answer.accept(byCustomer(true));

        Reservation after = fakes.reservations.get(RESERVATION);
        assertThat(after).as("the same reservation, not a new one").isSameAs(before);
        assertThat(after.reservationId()).isEqualTo(RESERVATION);
        assertThat(after.departureId()).isEqualTo(PROPOSED);
        assertThat(after.pendingBalance()).isEqualTo(pending);
        assertThat(after.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
        assertThat(closed.state()).isEqualTo(EpisodeState.RESCHEDULED);
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.episodeId()).isEqualTo(EPISODE);
            assertThat(record.originalReservationId()).isEqualTo(RESERVATION);
            assertThat(record.linkedReservationId()).isNull();
            assertThat(record.agreedSalidaId()).isEqualTo(PROPOSED);
            assertThat(record.agreedStartAt()).isEqualTo(NEW_START);
            assertThat(record.approvedBy()).isEqualTo(CUSTOMER);
            assertThat(record.recordedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void acceptingMovesTheSeatAndReleasesTheHoldInOneOperation() {
        answer.accept(byCustomer(true));

        assertThat(journal).contains("seat:" + RESERVATION + "/" + PROPOSED);
        assertThat(fakes.reservations.get(RESERVATION).seatHeld()).isTrue();
        assertThat(departures.rows.get(PROPOSED).heldSeats()).as("the seats were held when the proposal was sent; accepting holds nothing twice").isEqualTo(3);
        assertThat(fakes.holdCalls).isZero();
    }

    @Test
    void acceptingAnInExecutionReservationReturnsItToConfirmed() {
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.IN_EXECUTION);

        answer.accept(byCustomer(true));

        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void acceptingResumesTheFrozenDeadlinesWithoutPassingTheNewStart() {
        answer.accept(byCustomer(true));

        assertThat(journal).contains("resume:" + RESERVATION + "@" + NOW + "<=" + NEW_START);
    }

    @Test
    void acceptingEmitsReservationRescheduledOnceWithoutPersonalData() {
        answer.accept(byCustomer(true));

        assertThat(fakes.events).filteredOn(event -> event instanceof ReservationRescheduled).singleElement().satisfies(event -> {
            ReservationRescheduled rescheduled = (ReservationRescheduled) event;
            assertThat(rescheduled.originalSalidaId()).isEqualTo(ORIGINAL);
            assertThat(rescheduled.agreedSalidaId()).isEqualTo(PROPOSED);
            assertThat(rescheduled.approvedBy()).isEqualTo(CUSTOMER);
            assertThat(rescheduled.linkedReservationId()).isNull();
        });
    }

    @Test
    void theCustomerAnswersOnlyTheirOwnProposalAndAStaffAnswerNeedsChannelAndEvidence() {
        assertThatThrownBy(() -> answer.accept(AnswerProposalCommand.customer(TENANT, OTHER, OTHER, EPISODE, true))).isInstanceOf(PaymentAccessDeniedException.class);
        assertThat(stored.get(EPISODE).state()).isEqualTo(EpisodeState.OPEN);

        ReschedulingEpisode closed = answer.accept(AnswerProposalCommand.outside(TENANT, ADMIN, Role.OPERATIONAL_COLLABORATOR, EPISODE, true, "WHATSAPP", "Chat with the customer, saved in the file",
                NOW.minusSeconds(300)));
        assertThat(closed.state()).isEqualTo(EpisodeState.RESCHEDULED);
        assertThat(closed.proposals().get(0).answer().channel()).isEqualTo("WHATSAPP");
        assertThat(records.get(0).approvedBy()).isEqualTo(ADMIN);
        assertThatThrownBy(() -> answer.accept(AnswerProposalCommand.outside(TENANT, CUSTOMER, Role.FINAL_CUSTOMER, EPISODE, true, "PHONE", "x", NOW))).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> answer.accept(AnswerProposalCommand.outside(TENANT, ADMIN, Role.MANAGER, EPISODE, true, "PHONE", "x", NOW))).isInstanceOf(PaymentAccessDeniedException.class);
    }

    @Test
    void aSecondAnswerIsRefused() {
        answer.accept(byCustomer(true));

        assertThatThrownBy(() -> answer.accept(byCustomer(true))).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(records).hasSize(1);
    }

    @Test
    void anAnswerAfterTheResponseDeadlineIsRefusedSayingTheExpiryApplies() {
        stored.put(EPISODE, ReschedulingEpisode.open(EPISODE, TENANT, RESERVATION, ORIGINAL, NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(5)))
                .propose(new UUID(18, 61), PROPOSED, 3, ADMIN, NOW.minus(Duration.ofDays(4)).plusSeconds(1).minus(Duration.ofHours(1)), NEW_START));

        assertThatThrownBy(() -> answer.accept(byCustomer(true))).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("expiry");
        assertThat(records).isEmpty();
        assertThat(fakes.reservations.get(RESERVATION).departureId()).isEqualTo(ORIGINAL);
    }
}
