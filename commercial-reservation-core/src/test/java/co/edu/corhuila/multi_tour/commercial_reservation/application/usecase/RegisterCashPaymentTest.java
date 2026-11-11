package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterCashCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisteredPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingPaymentDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentValidated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationConfirmed;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-06 of HU-COM-009, the core part: RN-05, RN-08, RN-16; AC-09.2, AC-09.5, AC-09.6 (cash), AC-09.9, AC-09.13 (excess), AC-09.19.
// The cash domain is a double (DP-02, DD-2): the real adapter and the saga wait for HU-CSH-001 and HU-WFL-002.
class RegisterCashPaymentTest {

    private static final UUID TENANT = new UUID(9, 0xb1), ACTOR = new UUID(9, 0xb3), CUSTOMER = new UUID(9, 0xb4),
            SERVICE = new UUID(9, 0xb5), DEPARTURE = new UUID(9, 0xb6), RESERVATION = new UUID(9, 0xb7), ATTEMPT = new UUID(9, 0xb9);
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final CashFakes cash = new CashFakes();
    private final RegisterCashPaymentUseCase register = new RegisterCashPaymentUseCase(fakes, departures, fakes,
            (tenant, service, type, method) -> true, fakes, fakes, fakes, fakes, cash, cash, cash, () -> ATTEMPT,
            Clock.fixed(NOW, ZoneOffset.UTC));

    RegisterCashPaymentTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 0));
        // Two people, 300.000, a deposit with a minimum of 100.000 (the fake answers it).
        fakes.reservations.put(RESERVATION, Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ACTOR, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.CASH, NOW));
    }

    private RegisterCashCommand command(Long amount) {
        return new RegisterCashCommand(TENANT, ACTOR, RESERVATION, amount);
    }

    @Test
    void cashRegisteredByTheOfficeCountsAsReceivedMoney() {
        RegisteredPayment result = register.register(command(100_000L));

        assertThat(result.attempt().validationStatus()).isNull();
        assertThat(result.attempt().registeredBy()).isEqualTo(ACTOR);
        assertThat(cash.income).containsExactly(RESERVATION + ":100000:" + ACTOR);
        assertThat(fakes.saved).hasSize(1);
        assertThat(fakes.heldSeats(DEPARTURE)).isEqualTo(2);
    }

    @Test
    void cashThatMeetsTheConditionConfirmsTheReservationAndEmitsReservationConfirmed() {
        register.register(command(100_000L));

        Reservation stored = fakes.reservations.get(RESERVATION);
        assertThat(stored.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(stored.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
        assertThat(stored.pendingBalance()).isEqualTo(new Cents(200_000));
        assertThat(fakes.events).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("PaymentEvidenceRegistered", "PaymentValidated", "ReservationConfirmed", "CapacityAllocated");
        assertThat(fakes.events.get(1)).isInstanceOfSatisfying(PaymentValidated.class, e -> {
            assertThat(e.amount()).isEqualTo(new Cents(100_000));
            assertThat(e.validatorId()).isEqualTo(ACTOR);
            assertThat(e.pendingBalance()).isEqualTo(new Cents(200_000));
        });
        assertThat(fakes.events.get(2)).isInstanceOfSatisfying(ReservationConfirmed.class,
                e -> assertThat(e.allocatedCapacity()).isEqualTo(2));
    }

    @Test
    void cashWithoutAnOpenCashSessionIsRejectedAndNothingIsStored() {
        cash.open = false;

        assertThatThrownBy(() -> register.register(command(100_000L))).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("cash session");

        assertThat(cash.income).isEmpty();
        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.holdCalls).isZero();
    }

    @Test
    void aCashPaymentWithoutAvailabilityReversesTheCashIncome() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 9));

        assertThatThrownBy(() -> register.register(command(100_000L))).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("availability");

        assertThat(cash.income).hasSize(1);
        assertThat(cash.reversed).hasSize(1);
        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.heldSeats(DEPARTURE)).isEqualTo(9);
    }

    @Test
    void theExcessOverTheBalanceIsHandedToTheCreditBalanceAndOnlyTheBalanceIsApplied() {
        register.register(command(350_000L));

        Reservation stored = fakes.reservations.get(RESERVATION);
        assertThat(stored.paymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
        assertThat(stored.pendingBalance()).isEqualTo(new Cents(0));
        assertThat(cash.excess).containsExactly(new Cents(50_000));
    }

    @Test
    void noExcessMeansTheCreditBalanceIsNotCalled() {
        register.register(command(100_000L));

        assertThat(cash.excess).isEmpty();
    }

    @Test
    void aLaterCashPaymentNeitherHoldsNorConfirmsAgain() {
        register.register(command(100_000L));
        fakes.events.clear();
        int holds = fakes.holdCalls;

        register.register(command(50_000L));

        assertThat(fakes.holdCalls).isEqualTo(holds);
        assertThat(fakes.events).extracting(e -> e.getClass().getSimpleName()).containsExactly("PaymentEvidenceRegistered", "PaymentValidated");
        assertThat(fakes.reservations.get(RESERVATION).pendingBalance()).isEqualTo(new Cents(150_000));
    }

    @Test
    void cashBelowTheMinimumOrWithoutAmountIsRefusedBeforeTheIncomeIsRecorded() {
        assertThatThrownBy(() -> register.register(command(50_000L))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("amountCents"));
        assertThatThrownBy(() -> register.register(command(null))).isInstanceOfSatisfying(MissingPaymentDataException.class,
                e -> assertThat(e.fields()).containsExactly("amountCents"));

        assertThat(cash.income).isEmpty();
        assertThat(fakes.holdCalls).isZero();
    }

    @Test
    void theDepartureAndTheClosedReservationRulesApplyToCashToo() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW, DepartureStatus.SCHEDULED, 10, 0));

        assertThatThrownBy(() -> register.register(command(100_000L))).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("started");
        assertThat(cash.income).isEmpty();
    }
}
