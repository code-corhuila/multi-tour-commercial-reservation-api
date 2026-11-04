package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-01 of HU-COM-012: RN-01, RN-02, RN-12; AC-12.1, AC-12.7, AC-12.13.
class ReservationModifyTest {

    private static final UUID ID = new UUID(12, 1), TENANT = new UUID(12, 2), CUSTOMER = new UUID(12, 3), DEPARTURE = new UUID(12, 4),
            SERVICE = new UUID(12, 5), CREATOR = new UUID(12, 6), RULE = new UUID(12, 7), OTHER_DEPARTURE = new UUID(12, 8);
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-11-20T08:00:00Z");
    private static final Cents MINIMUM = new Cents(100_000);
    private static final ReservedService TOUR = new ReservedService(SERVICE, new Cents(100_000), new Cents(20_000));

    private static Reservation born() {
        FixedDiscount tenPercent = new FixedDiscount(1, RULE, "Early", DiscountType.PERCENTAGE, 1000, null,
                DiscountBase.PREVIOUS_SUBTOTAL, new Cents(360_000), new Cents(36_000), new Cents(324_000), null);
        return Reservation.create(ID, TENANT, CUSTOMER, DEPARTURE, CREATOR, 3, TOUR, new Cents(360_000), new Cents(324_000),
                List.of(tenPercent), PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW);
    }

    private static Companion companion(String document) {
        return new Companion("Ana Gomez", DocumentNumber.of(document), BirthDateOrAge.ofAge(25), null);
    }

    @Test
    void addingATravellerRecomputesValueWithTheDiscountsFixedAtCreation() {
        Reservation reservation = born();

        ModificationOutcome outcome = reservation.modify(4, TOUR, DEPARTURE, List.of(), START, NOW);

        assertThat(reservation.people()).isEqualTo(4);
        assertThat(reservation.projectedValue()).isEqualTo(new Cents(480_000));
        assertThat(reservation.finalValue()).isEqualTo(new Cents(432_000));
        assertThat(reservation.fixedDiscounts()).hasSize(1);
        assertThat(reservation.fixedDiscounts().get(0).ruleId()).isEqualTo(RULE);
        assertThat(reservation.fixedDiscounts().get(0).amount()).isEqualTo(new Cents(48_000));
        assertThat(outcome.oldFinalValue()).isEqualTo(new Cents(324_000));
        assertThat(outcome.newFinalValue()).isEqualTo(new Cents(432_000));
        assertThat(outcome.seatDelta()).isEqualTo(1);
    }

    @Test
    void registeredPaymentsStayInsideTheNewBalance() {
        Reservation reservation = born();
        reservation.registerCash(new UUID(12, 20), new Cents(100_000), MINIMUM, CREATOR, NOW);

        ModificationOutcome outcome = reservation.modify(4, TOUR, DEPARTURE, List.of(), START, NOW);

        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(332_000));
        assertThat(outcome.newPendingBalance()).isEqualTo(new Cents(332_000));
        assertThat(outcome.excess()).isEqualTo(new Cents(0));
    }

    @Test
    void aDecreaseUnderTheValidatedPaymentsLeavesZeroPendingAndTheExcess() {
        Reservation reservation = born();
        reservation.registerCash(new UUID(12, 21), new Cents(300_000), MINIMUM, CREATOR, NOW);

        ModificationOutcome outcome = reservation.modify(2, TOUR, DEPARTURE, List.of(), START, NOW);

        assertThat(reservation.finalValue()).isEqualTo(new Cents(216_000));
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(0));
        assertThat(outcome.excess()).isEqualTo(new Cents(84_000));
        assertThat(outcome.seatDelta()).isEqualTo(-1);
    }

    @Test
    void theGeneralDiscountsOfTodayAreNeverRead() {
        Reservation reservation = born();

        reservation.modify(4, TOUR, DEPARTURE, List.of(), START, NOW);

        assertThat(reservation.fixedDiscounts()).extracting(FixedDiscount::ruleId).containsExactly(RULE);
        assertThat(reservation.fixedDiscounts().get(0).percentageBasisPoints()).isEqualTo(1000);
    }

    @Test
    void aStartedFinishedOrCancelledReservationCannotBeModified() {
        Reservation started = born();
        assertThatThrownBy(() -> started.modify(4, TOUR, DEPARTURE, List.of(), NOW, NOW))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(started.people()).isEqualTo(3);

        Reservation cancelled = born();
        cancelled.cancel(CancellationCause.CUSTOMER_REQUEST, CREATOR, NOW, 0);
        assertThatThrownBy(() -> cancelled.modify(4, TOUR, DEPARTURE, List.of(), START, NOW))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void companionsFollowTheGroupRules() {
        Reservation reservation = born();

        assertThatThrownBy(() -> reservation.modify(2, TOUR, DEPARTURE, List.of(companion("1"), companion("2")), START, NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> reservation.modify(4, TOUR, DEPARTURE, List.of(companion("1"), companion("1")), START, NOW))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThat(reservation.people()).isEqualTo(3);

        reservation.modify(4, TOUR, OTHER_DEPARTURE, List.of(companion("1")), START, NOW);
        assertThat(reservation.companions()).hasSize(1);
        assertThat(reservation.departureId()).isEqualTo(OTHER_DEPARTURE);
    }
}
