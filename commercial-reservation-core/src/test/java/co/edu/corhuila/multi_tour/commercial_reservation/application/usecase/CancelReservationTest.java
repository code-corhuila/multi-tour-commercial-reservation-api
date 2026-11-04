package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingReservationDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;
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
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-013 T-01 to T-04: RN-01 to RN-05, RN-07, RN-10; AC-13.1 to AC-13.7, AC-13.9, AC-13.10, AC-13.13. The cancellation the customer asks
// for reuses Reservation.cancel of HU-COM-011; the settlement, the record, the seat release, the events and the opening of the decision
// commit together. The policy, the rescheduling mark and the decision are doubles (DP-02, HU-COM-018, HU-COM-016).
class CancelReservationTest {

    private static final UUID TENANT = new UUID(13, 0xa1), COLLABORATOR = new UUID(13, 0xa2), CUSTOMER = new UUID(13, 0xa3),
            SERVICE = new UUID(13, 0xa4), DEPARTURE = new UUID(13, 0xa5), RESERVATION = new UUID(13, 0xa6), OTHER_TENANT = new UUID(13, 0xa7);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant NOW = CREATED.plus(Duration.ofDays(2));
    private static final Instant START = CREATED.plus(Duration.ofDays(30));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final CancellationFakes cancellation = new CancellationFakes();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final CancelReservationUseCase cancel = new CancelReservationUseCase(fakes, departures, fakes, cancellation, cancellation,
            cancellation, cancellation, fakes, fakes, clock);
    private final QuoteCancellationUseCase quote = new QuoteCancellationUseCase(fakes, departures, cancellation, cancellation, clock);
    private final GetCancellationRecordUseCase read = new GetCancellationRecordUseCase(cancellation);

    CancelReservationTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, START, DepartureStatus.SCHEDULED, 10, 2));
    }

    private Reservation planted(ReservationStatus status, long pendingBalance) {
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, COLLABORATOR, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, CREATED);
        reservation.restoredAs(status, PaymentStatus.PARTIAL, new Cents(pendingBalance), false, List.of());
        fakes.reservations.put(RESERVATION, reservation);
        return reservation;
    }

    private static PaymentActor actor(Role role) {
        return new PaymentActor(TENANT, COLLABORATOR, role);
    }

    private CancelReservationCommand command(Role role, String reason, String origin) {
        return new CancelReservationCommand(actor(role), RESERVATION, reason, origin);
    }

    private CancellationRecord register() {
        return cancel.cancel(command(Role.OPERATIONAL_COLLABORATOR, "  The customer cannot travel  ", "CUSTOMER_REQUEST"));
    }

    private static List<String> names(List<PaymentEvent> events) {
        return events.stream().map(e -> e.getClass().getSimpleName()).toList();
    }

    @Test
    void aPendingOrConfirmedReservationBecomesCancelledByCustomerRequest() {
        for (ReservationStatus status : List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED)) {
            fakes.saved.clear();
            Reservation reservation = planted(status, 0);

            CancellationRecord record = register();

            Reservation stored = fakes.reservations.get(RESERVATION);
            assertThat(stored).isSameAs(reservation);
            assertThat(stored.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(stored.cancellationCause()).isEqualTo(CancellationCause.CUSTOMER_REQUEST);
            assertThat(stored.cancelledBy()).isEqualTo(COLLABORATOR);
            assertThat(stored.cancelledAt()).isEqualTo(NOW);
            assertThat(record.cancelledAt()).isEqualTo(NOW);
        }
    }

    @Test
    void anInExecutionFinishedOrCancelledReservationCannotBeCancelled() {
        for (ReservationStatus status : List.of(ReservationStatus.IN_EXECUTION, ReservationStatus.COMPLETED, ReservationStatus.CANCELLED)) {
            planted(status, 0);

            assertThatThrownBy(this::register).isInstanceOf(InvalidStatusTransitionException.class);

            assertThat(fakes.saved).isEmpty();
            assertThat(fakes.events).isEmpty();
            assertThat(fakes.releaseCalls).isZero();
            assertThat(cancellation.records).isEmpty();
        }
    }

    @Test
    void anEmptyReasonOrAForeignOriginIsRefusedNamingTheField() {
        planted(ReservationStatus.PENDING_PAYMENT, 0);

        assertThatThrownBy(() -> cancel.cancel(command(Role.OPERATIONAL_COLLABORATOR, "   ", "CUSTOMER_REQUEST")))
                .isInstanceOfSatisfying(MissingReservationDataException.class, e -> assertThat(e.fields()).containsExactly("reason"));
        assertThatThrownBy(() -> cancel.cancel(command(Role.OPERATIONAL_COLLABORATOR, "Because", "PAYMENT_EXPIRY")))
                .isInstanceOfSatisfying(MissingReservationDataException.class, e -> assertThat(e.fields()).containsExactly("origin"));
        assertThatThrownBy(() -> cancel.cancel(command(Role.OPERATIONAL_COLLABORATOR, "", null)))
                .isInstanceOfSatisfying(MissingReservationDataException.class, e -> assertThat(e.fields()).containsExactly("reason", "origin"));
        assertThatThrownBy(() -> cancel.cancel(command(Role.OPERATIONAL_COLLABORATOR, "x".repeat(501), "CUSTOMER_REQUEST")))
                .isInstanceOfSatisfying(MissingReservationDataException.class, e -> assertThat(e.fields()).containsExactly("reason"));
        assertThat(fakes.saved).isEmpty();
    }

    @Test
    void refundableIsValidatedMinusPenaltyPdrExample() {
        planted(ReservationStatus.CONFIRMED, 0);
        cancellation.basisPoints = OptionalInt.of(5_000);

        CancellationRecord record = register();

        assertThat(record.settlement().validated()).isEqualTo(new Cents(300_000));
        assertThat(record.settlement().penalty()).isEqualTo(new Cents(150_000));
        assertThat(record.settlement().refundable()).isEqualTo(new Cents(150_000));
        assertThat(record.settlement().penaltyBasisPoints()).isEqualTo(5_000);
    }

    @Test
    void valuesPendingValidationDoNotCount() {
        planted(ReservationStatus.CONFIRMED, 100_000);

        CancellationRecord record = register();

        assertThat(record.settlement().validated()).isEqualTo(new Cents(200_000));
        assertThat(record.settlement().refundable()).isEqualTo(new Cents(200_000));
    }

    @Test
    void theRecordKeepsReasonOriginAmountsActorAndInstant() {
        planted(ReservationStatus.CONFIRMED, 0);
        cancellation.basisPoints = OptionalInt.of(2_500);

        register();

        CancellationRecord stored = cancellation.records.get(RESERVATION);
        assertThat(stored.reason()).isEqualTo("The customer cannot travel");
        assertThat(stored.origin()).isEqualTo(CancellationCause.CUSTOMER_REQUEST);
        assertThat(stored.settlement().penalty()).isEqualTo(new Cents(75_000));
        assertThat(stored.cancelledBy()).isEqualTo(COLLABORATOR);
        assertThat(stored.cancelledAt()).isEqualTo(NOW);
        assertThat(read.get(actor(Role.ANALYST), RESERVATION)).isEqualTo(stored);
    }

    @Test
    void refundableAboveZeroOpensTheDecisionWithItsStart() {
        planted(ReservationStatus.CONFIRMED, 0);
        cancellation.basisPoints = OptionalInt.of(5_000);

        CancellationRecord record = register();

        assertThat(cancellation.opened).containsExactly("300000/150000 at " + NOW);
        assertThat(record.refundDecisionOpened()).isTrue();
    }

    @Test
    void zeroRefundOpensNoDecision() {
        planted(ReservationStatus.PENDING_PAYMENT, 300_000);

        CancellationRecord record = register();

        assertThat(record.settlement().refundable()).isEqualTo(new Cents(0));
        assertThat(cancellation.opened).isEmpty();
        assertThat(record.refundDecisionOpened()).isFalse();
    }

    @Test
    void aReservationPendingAReschedulingAnswerIsNotCancelled() {
        planted(ReservationStatus.CONFIRMED, 0);
        cancellation.marked.add(RESERVATION);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("reject");

        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.releaseCalls).isZero();
        assertThat(cancellation.records).isEmpty();
    }

    @Test
    void theMomentOfCancellationDrivesThePenaltyAndNeverBlocksIt() {
        planted(ReservationStatus.CONFIRMED, 0);
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plus(Duration.ofMinutes(5)), DepartureStatus.SCHEDULED, 10, 2));
        cancellation.basisPoints = OptionalInt.of(10_000);

        CancellationRecord record = register();

        assertThat(cancellation.policyAskedAt).containsExactly(NOW);
        assertThat(record.settlement().refundable()).isEqualTo(new Cents(0));
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancellationReleasesTheHeldSeatInTheSameTransaction() {
        Reservation reservation = planted(ReservationStatus.CONFIRMED, 0);
        reservation.allocateSeat(CREATED);
        reservation.pullEvents();

        register();

        assertThat(fakes.heldSeats(DEPARTURE)).isZero();
        assertThat(names(fakes.events)).containsExactly("CapacityReleased", "ReservationCancelled");
        assertThat(fakes.reservations.get(RESERVATION).seatHeld()).isFalse();
    }

    @Test
    void customerManagerAccountantAndAnalystCannotCancel() {
        planted(ReservationStatus.CONFIRMED, 0);

        for (Role role : List.of(Role.FINAL_CUSTOMER, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST)) {
            assertThatThrownBy(() -> cancel.cancel(command(role, "Because", "CUSTOMER_REQUEST"))).isInstanceOf(PaymentAccessDeniedException.class);
            assertThatThrownBy(() -> quote.quote(actor(role), RESERVATION)).isInstanceOf(PaymentAccessDeniedException.class);
        }
        assertThat(fakes.saved).isEmpty();
        assertThat(cancellation.records).isEmpty();
    }

    @Test
    void aReservationOfAnotherTenantAnswersNotFound() {
        planted(ReservationStatus.CONFIRMED, 0);
        PaymentActor stranger = new PaymentActor(OTHER_TENANT, COLLABORATOR, Role.TENANT_ADMINISTRATOR);

        assertThatThrownBy(() -> cancel.cancel(new CancelReservationCommand(stranger, RESERVATION, "Because", "CUSTOMER_REQUEST")))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> quote.quote(stranger, RESERVATION)).isInstanceOf(ReservationNotFoundException.class);
        assertThat(fakes.saved).isEmpty();
    }

    @Test
    void theQuoteCarriesNoSideEffect() {
        planted(ReservationStatus.CONFIRMED, 0);
        cancellation.basisPoints = OptionalInt.of(5_000);

        CancellationQuote answer = quote.quote(actor(Role.OPERATIONAL_COLLABORATOR), RESERVATION);

        assertThat(answer.cancellable()).isTrue();
        assertThat(answer.settlement().refundable()).isEqualTo(new Cents(150_000));
        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.releaseCalls).isZero();
        assertThat(cancellation.records).isEmpty();
        assertThat(cancellation.opened).isEmpty();
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void theQuoteSaysWhyAReservationCannotBeCancelled() {
        planted(ReservationStatus.IN_EXECUTION, 0);
        assertThat(quote.quote(actor(Role.OPERATIONAL_COLLABORATOR), RESERVATION).refusal())
                .isEqualTo(CancellationQuote.Refusal.INVALID_STATUS_TRANSITION);

        planted(ReservationStatus.CONFIRMED, 0);
        cancellation.marked.add(RESERVATION);
        CancellationQuote marked = quote.quote(actor(Role.OPERATIONAL_COLLABORATOR), RESERVATION);
        assertThat(marked.cancellable()).isFalse();
        assertThat(marked.refusal()).isEqualTo(CancellationQuote.Refusal.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void theRecordIsReadOnlyByStaffAndAnUnknownOneIsNotFound() {
        assertThatThrownBy(() -> read.get(actor(Role.MANAGER), RESERVATION)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> read.get(actor(Role.FINAL_CUSTOMER), RESERVATION)).isInstanceOf(PaymentAccessDeniedException.class);
    }
}
