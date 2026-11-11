package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterTransferCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisteredPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingPaymentDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03 to T-05 of HU-COM-009, the core part: RN-03, RN-06 to RN-08, RN-12, RN-16, RN-17; AC-09.1, AC-09.6 to AC-09.9, AC-09.11,
// AC-09.14, AC-09.21. The conditional update, the schema and the race with two real connections wait for the JDBC level.
class RegisterTransferTest {

    private static final UUID TENANT = new UUID(9, 0xa1), OTHER_TENANT = new UUID(9, 0xa2), ACTOR = new UUID(9, 0xa3),
            CUSTOMER = new UUID(9, 0xa4), SERVICE = new UUID(9, 0xa5), DEPARTURE = new UUID(9, 0xa6), RESERVATION = new UUID(9, 0xa7),
            SECOND_RESERVATION = new UUID(9, 0xa8), ATTEMPT = new UUID(9, 0xa9);
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private boolean enabled = true;
    private final RegisterTransferUseCase register = new RegisterTransferUseCase(fakes, departures, fakes, (tenant, service, type, method) -> enabled,
            fakes, fakes, fakes, fakes, fakes, () -> ATTEMPT, Clock.fixed(NOW, ZoneOffset.UTC));

    RegisterTransferTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 0));
        plant(RESERVATION, 2);
    }

    // Two people at 100.000 plus 50.000 of transport each: 300.000, a deposit with a minimum of 100.000.
    private Reservation plant(UUID id, int people) {
        Reservation reservation = Reservation.create(id, TENANT, CUSTOMER, DEPARTURE, ACTOR, people,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(150_000L * people), new Cents(150_000L * people),
                List.of(), PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW);
        fakes.reservations.put(id, reservation);
        return reservation;
    }

    private RegisterTransferCommand command(UUID reservation, Long amount, byte[] proof) {
        return new RegisterTransferCommand(TENANT, ACTOR, reservation, amount, proof);
    }

    @Test
    void aTransferIsStoredInValidationWithItsProofTheSeatAndTheEventsAndChangesNothingElse() {
        RegisteredPayment result = register.register(command(RESERVATION, 100_000L, PNG));

        Reservation stored = fakes.reservations.get(RESERVATION);
        assertThat(result.attempt().validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
        assertThat(stored.attempts()).hasSize(1);
        assertThat(stored.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(stored.paymentStatus()).isEqualTo(PaymentStatus.NO_PAYMENT);
        assertThat(stored.seatHeld()).isTrue();
        assertThat(fakes.proofs.get(ATTEMPT).contentType()).isEqualTo("image/png");
        assertThat(fakes.heldSeats(DEPARTURE)).isEqualTo(2);
        assertThat(fakes.events).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("PaymentEvidenceRegistered", "CapacityAllocated");
    }

    @Test
    void theAttemptRecordsTheUserOfTheCommandAndEveryWriteRunsInsideTheTransaction() {
        RegisteredPayment result = register.register(command(RESERVATION, 100_000L, PNG));

        assertThat(result.attempt().registeredBy()).isEqualTo(ACTOR);
        assertThat(result.attempt().registeredAt()).isEqualTo(NOW);
        assertThat(fakes.saved).hasSize(1);
    }

    @Test
    void aTransferInValidationProducesNoCashIncomeAndNoValidatedPayment() {
        register.register(command(RESERVATION, 100_000L, PNG));

        assertThat(fakes.events).extracting(e -> e.getClass().getSimpleName()).doesNotContain("PaymentValidated", "ReservationConfirmed");
    }

    @Test
    void theFirstPaymentWithoutAvailabilityRegistersNothing() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 9));

        assertThatThrownBy(() -> register.register(command(RESERVATION, 100_000L, PNG))).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("availability");

        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.proofs).isEmpty();
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.heldSeats(DEPARTURE)).isEqualTo(9);
    }

    @Test
    void twoAttemptsForTheLastSeatLeaveOneWinnerAndTheSameAnswerForTheOther() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 2, 0));
        plant(SECOND_RESERVATION, 2);

        register.register(command(RESERVATION, 100_000L, PNG));
        assertThatThrownBy(() -> register.register(command(SECOND_RESERVATION, 100_000L, PNG))).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("availability");

        assertThat(fakes.heldSeats(DEPARTURE)).isEqualTo(2);
        assertThat(fakes.proofs).hasSize(1);
    }

    @Test
    void aLaterPaymentNeitherHoldsNorReleasesCapacity() {
        register.register(command(RESERVATION, 100_000L, PNG));
        fakes.events.clear();
        int holds = fakes.holdCalls;

        register.register(command(RESERVATION, 50_000L, PNG));

        assertThat(fakes.holdCalls).isEqualTo(holds);
        assertThat(fakes.heldSeats(DEPARTURE)).isEqualTo(2);
        assertThat(fakes.events).extracting(e -> e.getClass().getSimpleName()).containsExactly("PaymentEvidenceRegistered");
    }

    @Test
    void noPaymentFromTheScheduledStartOrOnACancelledDeparture() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW, DepartureStatus.SCHEDULED, 10, 0));
        assertThatThrownBy(() -> register.register(command(RESERVATION, 100_000L, PNG))).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("started");

        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plusSeconds(60), DepartureStatus.CANCELLED, 10, 0));
        assertThatThrownBy(() -> register.register(command(RESERVATION, 100_000L, PNG))).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("cancelled");

        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.holdCalls).isZero();
    }

    @Test
    void noPaymentAfterTheDeadlineOrOnAClosedReservation() {
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW));
        assertThatThrownBy(() -> register.register(command(RESERVATION, 100_000L, PNG))).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("deadline");

        fakes.deadlines.clear();
        fakes.reservations.put(RESERVATION, fakes.reservations.get(RESERVATION).restoredAs(ReservationStatus.CANCELLED,
                PaymentStatus.NO_PAYMENT, new Cents(300_000), false, List.of()));
        assertThatThrownBy(() -> register.register(command(RESERVATION, 100_000L, PNG))).isInstanceOf(BusinessRuleViolation.class);

        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.holdCalls).isZero();
    }

    @Test
    void aReservationWithoutADeadlineRowIsNotRefusedForExpiryAndADeadlineInTheFutureAccepts() {
        register.register(command(RESERVATION, 100_000L, PNG));
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.BALANCE, NOW.plusSeconds(1)));

        register.register(command(RESERVATION, 50_000L, PNG));

        assertThat(fakes.reservations.get(RESERVATION).attempts()).hasSize(2);
    }

    @Test
    void aProofIsJudgedFromItsContentAndNothingIsStoredWhenItIsNot() {
        byte[] text = "a text renamed proof.pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> register.register(command(RESERVATION, 100_000L, text))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("proof"));

        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.holdCalls).isZero();
    }

    @Test
    void aMissingAmountOrProofIsNamedTogether() {
        assertThatThrownBy(() -> register.register(command(RESERVATION, null, null))).isInstanceOfSatisfying(
                MissingPaymentDataException.class, e -> assertThat(e.fields()).containsExactly("amountCents", "proof"));
        assertThatThrownBy(() -> register.register(command(RESERVATION, 0L, PNG))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("amountCents"));
        assertThat(fakes.holdCalls).isZero();
    }

    @Test
    void aTypeOrMethodNotEnabledIsRefusedNamingTheFieldAndHoldsNothing() {
        enabled = false;

        assertThatThrownBy(() -> register.register(command(RESERVATION, 100_000L, PNG))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("paymentMethod"));

        assertThat(fakes.holdCalls).isZero();
    }

    @Test
    void aDepositBelowTheMinimumIsRefusedBeforeAnySeatIsHeld() {
        assertThatThrownBy(() -> register.register(command(RESERVATION, 99_999L, PNG))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("amountCents"));

        assertThat(fakes.holdCalls).isZero();
        assertThat(fakes.heldSeats(DEPARTURE)).isZero();
    }

    @Test
    void aReservationOfAnotherTenantOrMissingAnswersNotFound() {
        assertThatThrownBy(() -> register.register(new RegisterTransferCommand(OTHER_TENANT, ACTOR, RESERVATION, 100_000L, PNG)))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> register.register(command(new UUID(9, 0xff), 100_000L, PNG))).isInstanceOf(ReservationNotFoundException.class);
    }
}
