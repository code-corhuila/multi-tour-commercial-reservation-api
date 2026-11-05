package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingDifferenceDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityAllocated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityReleased;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationChanged;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangePolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ModificationOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03 to T-05 of HU-COM-012: RN-04 to RN-07, RN-09; AC-12.2 to AC-12.5, AC-12.11, AC-12.12. The terms (DP-01), the decision of the excess
// (HU-COM-016, declared debt DD-3) and the pending-difference row are doubles; the seats go through the capacity holder.
class ModifyReservationTest {

    private static final UUID TENANT = new UUID(12, 0xc1), COLLABORATOR = new UUID(12, 0xc2), CUSTOMER = new UUID(12, 0xc3),
            SERVICE = new UUID(12, 0xc4), DEPARTURE = new UUID(12, 0xc5), OTHER = new UUID(12, 0xc6), RESERVATION = new UUID(12, 0xc7);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant NOW = CREATED.plus(Duration.ofDays(2));
    private static final Instant START = CREATED.plus(Duration.ofDays(30));
    private static final ReservedService TOUR = new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final List<String> excesses = new ArrayList<>();
    private final List<Instant> differences = new ArrayList<>();
    private OptionalInt advanceHours = OptionalInt.of(48);
    private final ExcessDecisionOpener decisions = (tenant, reservation, excess, at) -> excesses.add(excess.amount() + "@" + at);
    private final PendingDifferenceDeadlines deadlines = new PendingDifferenceDeadlines() {
        @Override
        public OptionalInt balanceAdvanceHours(UUID tenantId, UUID serviceId, PaymentType type) {
            return advanceHours;
        }

        @Override
        public void record(UUID tenantId, UUID reservationId, Instant dueAt) {
            differences.add(dueAt);
        }
    };
    private final ReservationModificationFakes history = new ReservationModificationFakes();
    private final ModifyReservationUseCase modify = new ModifyReservationUseCase(fakes, departures, (tenant, service, type) -> Optional.of(new ChangePolicy(true, 60)),
            fakes, fakes, decisions, deadlines, history, fakes, fakes, Clock.fixed(NOW, ZoneOffset.UTC));

    ModifyReservationTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, START, DepartureStatus.SCHEDULED, 10, 2));
        departures.plant(new DepartureState(OTHER, TENANT, SERVICE, START.plus(Duration.ofDays(7)), DepartureStatus.SCHEDULED, 10, 0));
    }

    private Reservation planted(PaymentType type, long paid) {
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, COLLABORATOR, 2, TOUR, new Cents(300_000),
                new Cents(300_000), List.of(), type, PaymentMethod.CASH, CREATED);
        reservation.registerCash(new UUID(12, 0xd1), new Cents(paid), new Cents(100_000), COLLABORATOR, CREATED);
        reservation.allocateSeat(CREATED);
        reservation.pullEvents();
        fakes.reservations.put(RESERVATION, reservation);
        return reservation;
    }

    private ModificationOutcome ask(int people, UUID departure, ChangeType type) {
        return modify.modify(new PaymentActor(TENANT, COLLABORATOR, Role.OPERATIONAL_COLLABORATOR), RESERVATION,
                new ModificationRequest(type, people, departure, List.of()), "  The customer asked for it  ").outcome();
    }

    @Test
    void anIncreaseKeepsTheReservationConfirmedAndCreatesTheDifferenceDeadline() {
        Reservation reservation = planted(PaymentType.DEPOSIT, 300_000);

        ModificationOutcome outcome = ask(3, DEPARTURE, ChangeType.MODIFICATION);

        assertThat(outcome.newPendingBalance()).isEqualTo(new Cents(150_000));
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
        assertThat(differences).containsExactly(START.minus(Duration.ofHours(48)));
        assertThat(fakes.saved).containsExactly(reservation);
    }

    @Test
    void aFullPaymentDifferenceIsDueAtTheDepartureStart() {
        planted(PaymentType.FULL_PAYMENT, 300_000);

        ask(3, DEPARTURE, ChangeType.MODIFICATION);

        assertThat(differences).containsExactly(START);
    }

    @Test
    void valueUnderValidatedPaymentsLeavesAnExcessHandedToTheDecision() {
        Reservation reservation = planted(PaymentType.FULL_PAYMENT, 300_000);

        ModificationOutcome outcome = ask(1, DEPARTURE, ChangeType.MODIFICATION);

        assertThat(outcome.excess()).isEqualTo(new Cents(150_000));
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(0));
        assertThat(excesses).containsExactly("150000@" + NOW);
        assertThat(differences).isEmpty();
    }

    @Test
    void moreTravellersThanCapacityIsRefusedAndNothingChanges() {
        Reservation reservation = planted(PaymentType.FULL_PAYMENT, 300_000);

        assertThatThrownBy(() -> ask(12, DEPARTURE, ChangeType.MODIFICATION)).isInstanceOf(BusinessRuleViolation.class);

        assertThat(reservation.people()).isEqualTo(2);
        assertThat(fakes.saved).isEmpty();
        assertThat(departures.rows.get(DEPARTURE).heldSeats()).isEqualTo(2);
    }

    @Test
    void fewerTravellersReleaseTheirSeatsInTheSameOperation() {
        planted(PaymentType.FULL_PAYMENT, 300_000);

        ask(1, DEPARTURE, ChangeType.MODIFICATION);

        assertThat(departures.rows.get(DEPARTURE).heldSeats()).isEqualTo(1);
        assertThat(fakes.releaseCalls).isEqualTo(1);
    }

    @Test
    void aDateChangeMovesTheSeatsAsOneOperation() {
        Reservation reservation = planted(PaymentType.FULL_PAYMENT, 300_000);

        ask(2, OTHER, ChangeType.DATE_CHANGE);

        assertThat(reservation.departureId()).isEqualTo(OTHER);
        assertThat(departures.rows.get(DEPARTURE).heldSeats()).isEqualTo(0);
        assertThat(departures.rows.get(OTHER).heldSeats()).isEqualTo(2);
    }

    @Test
    void aDateChangeWithoutCapacityKeepsDateAndSeats() {
        Reservation reservation = planted(PaymentType.FULL_PAYMENT, 300_000);
        departures.plant(new DepartureState(OTHER, TENANT, SERVICE, START.plus(Duration.ofDays(7)), DepartureStatus.SCHEDULED, 1, 0));

        assertThatThrownBy(() -> ask(2, OTHER, ChangeType.DATE_CHANGE)).isInstanceOf(BusinessRuleViolation.class);

        assertThat(reservation.departureId()).isEqualTo(DEPARTURE);
        assertThat(departures.rows.get(DEPARTURE).heldSeats()).isEqualTo(2);
    }

    @Test
    void aDateChangeWhileAProofIsInValidationIsRefused() {
        Reservation reservation = planted(PaymentType.DEPOSIT, 100_000);
        reservation.registerTransfer(new UUID(12, 0xd2), new Cents(50_000), new Cents(100_000), COLLABORATOR, CREATED);

        assertThatThrownBy(() -> ask(2, OTHER, ChangeType.DATE_CHANGE)).isInstanceOf(BusinessRuleViolation.class);

        assertThat(reservation.departureId()).isEqualTo(DEPARTURE);
    }

    @Test
    void theTermsRefuseTheChangeNamingTheReasonAndNothingChanges() {
        Reservation reservation = planted(PaymentType.FULL_PAYMENT, 300_000);
        ModifyReservationUseCase refusing = new ModifyReservationUseCase(fakes, departures, (tenant, service, type) -> Optional.empty(), fakes,
                fakes, decisions, deadlines, fakes, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> refusing.modify(new PaymentActor(TENANT, COLLABORATOR, Role.OPERATIONAL_COLLABORATOR), RESERVATION,
                new ModificationRequest(ChangeType.MODIFICATION, 3, DEPARTURE, List.of()), "reason"))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("NO_POLICY");

        assertThat(reservation.people()).isEqualTo(2);
        assertThat(fakes.saved).isEmpty();
    }

    @Test
    void everyChangeStoresOneImmutableRowWithBeforeAfterReasonAuthorAndInstant() {
        planted(PaymentType.FULL_PAYMENT, 300_000);

        ask(3, DEPARTURE, ChangeType.MODIFICATION);

        assertThat(history.rows).hasSize(1);
        var row = history.rows.get(0);
        assertThat(row.reservationId()).isEqualTo(RESERVATION);
        assertThat(row.changeType()).isEqualTo(ChangeType.MODIFICATION);
        assertThat(row.reason()).isEqualTo("The customer asked for it");
        assertThat(row.outcome().oldFinalValue()).isEqualTo(new Cents(300_000));
        assertThat(row.outcome().newFinalValue()).isEqualTo(new Cents(450_000));
        assertThat(row.previousPartySize()).isEqualTo(2);
        assertThat(row.newPartySize()).isEqualTo(3);
        assertThat(row.registeredBy()).isEqualTo(COLLABORATOR);
        assertThat(row.registeredAt()).isEqualTo(NOW);
    }

    @Test
    void theEventsCarryNoReasonAndTheSeatsChangeIsAnnounced() {
        planted(PaymentType.FULL_PAYMENT, 300_000);

        ask(3, DEPARTURE, ChangeType.MODIFICATION);

        assertThat(fakes.events).hasAtLeastOneElementOfType(ReservationChanged.class).hasAtLeastOneElementOfType(CapacityAllocated.class);
        ReservationChanged event = fakes.events.stream().filter(ReservationChanged.class::isInstance).map(ReservationChanged.class::cast)
                .findFirst().orElseThrow();
        assertThat(event.changeType()).isEqualTo(ChangeType.MODIFICATION);
        assertThat(event.newFinalValue()).isEqualTo(new Cents(450_000));
        assertThat(event.partySize()).isEqualTo(3);
        assertThat(event.departureChanged()).isFalse();
        assertThat(event.toString()).doesNotContain("customer asked");

        fakes.events.clear();
        ask(2, DEPARTURE, ChangeType.MODIFICATION);
        assertThat(fakes.events).hasAtLeastOneElementOfType(CapacityReleased.class);
    }

    @Test
    void aRefusedChangeStoresNoRowAndNoEvent() {
        planted(PaymentType.FULL_PAYMENT, 300_000);

        assertThatThrownBy(() -> ask(12, DEPARTURE, ChangeType.MODIFICATION)).isInstanceOf(BusinessRuleViolation.class);

        assertThat(history.rows).isEmpty();
        assertThat(fakes.events).isEmpty();
    }
}
