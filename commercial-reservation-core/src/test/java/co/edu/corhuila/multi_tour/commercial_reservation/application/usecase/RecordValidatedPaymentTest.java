package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordValidatedPaymentCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingPaymentDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Step 2 of the saga register-payment (HU-COM-010 DP-11, answered 2026-10-30): the human approval happened before the saga, so
// this operation only records the validated payment of the proof, idempotently, for the user the saga forwards. The caller is
// trusted by its SERVICE role (the adapter), so no permission of the user is checked here.
class RecordValidatedPaymentTest {

    private static final UUID TENANT = new UUID(11, 0xb1), APPROVER = new UUID(11, 0xb2), CUSTOMER = new UUID(11, 0xb3), SERVICE = new UUID(11, 0xb4),
            DEPARTURE = new UUID(11, 0xb5), RESERVATION = new UUID(11, 0xb6), FIRST = new UUID(11, 0xb7), OTHER_TENANT = new UUID(11, 0xba);
    private static final Instant REGISTERED = Instant.parse("2026-10-29T10:00:00Z");
    private static final Instant NOW = REGISTERED.plus(Duration.ofHours(3));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final ReviewFakes review = new ReviewFakes(fakes);
    private final CashFakes cash = new CashFakes();
    private final DecidePaymentProofUseCase decide = new DecidePaymentProofUseCase(fakes, fakes, fakes, fakes, review, review, cash, fakes, fakes,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final RecordValidatedPaymentUseCase record = new RecordValidatedPaymentUseCase(decide, fakes);

    RecordValidatedPaymentTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plus(Duration.ofDays(5)), DepartureStatus.SCHEDULED, 10, 2));
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, APPROVER, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, REGISTERED);
        reservation.registerTransfer(FIRST, new Cents(100_000), new Cents(100_000), APPROVER, REGISTERED);
        reservation.allocateSeat(REGISTERED);
        reservation.pullEvents();
        fakes.reservations.put(RESERVATION, reservation);
    }

    private DecidedProof record(UUID tenant, Long verified, String reason) {
        return record.record(new RecordValidatedPaymentCommand(tenant, APPROVER, FIRST, verified, reason));
    }

    @Test
    void theSecondStepRecordsTheValidatedPaymentForTheUserTheSagaForwards() {
        DecidedProof decided = record(TENANT, 100_000L, null);

        Reservation stored = fakes.reservations.get(RESERVATION);
        assertThat(decided.attempt().validationStatus()).isEqualTo(ValidationStatus.APPROVED);
        assertThat(decided.attempt().decidedBy()).isEqualTo(APPROVER);
        assertThat(stored.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(stored.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
        assertThat(stored.pendingBalance()).isEqualTo(new Cents(200_000));
        assertThat(fakes.events.stream().map(e -> e.getClass().getSimpleName()).toList()).containsExactly("PaymentValidated", "ReservationConfirmed");
    }

    @Test
    void aRepeatedCallWithTheSameDecisionAnswersTheStoredOneAndWritesNothing() {
        DecidedProof first = record(TENANT, 100_000L, null);
        fakes.saved.clear();
        fakes.events.clear();

        DecidedProof again = record(TENANT, 100_000L, null);

        assertThat(again.attempt()).isEqualTo(first.attempt());
        assertThat(again.applied()).isEqualTo(first.applied());
        assertThat(again.excess()).isEqualTo(first.excess());
        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.reservations.get(RESERVATION).pendingBalance()).isEqualTo(new Cents(200_000));
    }

    @Test
    void aRepeatedCallWithAnotherAmountOrOnARejectedProofIsAFinalDecisionNotAReplay() {
        record(TENANT, 100_000L, null);

        assertThatThrownBy(() -> record(TENANT, 90_000L, "other")).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fakes.reservations.get(RESERVATION).pendingBalance()).isEqualTo(new Cents(200_000));
    }

    @Test
    void aRejectedProofIsNeverRecordedAsValidated() {
        decide.reject(new RejectProofCommand(new PaymentActor(TENANT, APPROVER, Role.TENANT_ADMINISTRATOR), FIRST, "illegible"));
        fakes.events.clear();

        assertThatThrownBy(() -> record(TENANT, 100_000L, null)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void aMissingAmountIsNamedAndAnotherTenantsProofLooksMissing() {
        assertThatThrownBy(() -> record(TENANT, null, null)).isInstanceOf(MissingPaymentDataException.class);
        assertThatThrownBy(() -> record(OTHER_TENANT, 100_000L, null)).isInstanceOf(PaymentProofNotFoundException.class);
        assertThat(fakes.saved).isEmpty();
    }
}
