package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-01 of HU-COM-007: RN-01, RN-07, RN-14; AC-07.1, AC-07.2, AC-07.18.
class ReservationTest {

    private static final UUID ID = new UUID(7, 1), TENANT = new UUID(7, 2), CUSTOMER = new UUID(7, 3), DEPARTURE = new UUID(7, 4),
            SERVICE = new UUID(7, 5), CREATOR = new UUID(7, 6), RULE = new UUID(7, 7);
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final ReservedService TOUR = new ReservedService(SERVICE, new Cents(100_000), new Cents(20_000));

    private static FixedDiscount line(int order, long base, long amount) {
        return new FixedDiscount(order, RULE, "Early", DiscountType.PERCENTAGE, 1000, null, DiscountBase.PREVIOUS_SUBTOTAL,
                new Cents(base), new Cents(amount), new Cents(base - amount), null);
    }

    private static Reservation born(List<FixedDiscount> discounts, long finalValue) {
        return Reservation.create(ID, TENANT, CUSTOMER, DEPARTURE, CREATOR, 3, TOUR, new Cents(360_000), new Cents(finalValue),
                discounts, PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW);
    }

    @Test
    void aReservationIsBornPendingPaymentWithNoPaymentAndPendingBalanceEqualToFinalValue() {
        Reservation reservation = born(List.of(line(1, 360_000, 36_000)), 324_000);

        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.NO_PAYMENT);
        assertThat(reservation.projectedValue()).isEqualTo(new Cents(360_000));
        assertThat(reservation.finalValue()).isEqualTo(new Cents(324_000));
        assertThat(reservation.pendingBalance()).isEqualTo(reservation.finalValue());
        assertThat(reservation.creditBalance()).isEqualTo(new Cents(0));
        assertThat(reservation.seatHeld()).isFalse();
        assertThat(reservation.createdAt()).isEqualTo(NOW);
        assertThat(reservation.tenantId()).isEqualTo(TENANT);
        assertThat(reservation.expectedPaymentType()).isEqualTo(PaymentType.DEPOSIT);
        assertThat(reservation.expectedPaymentMethod()).isEqualTo(PaymentMethod.TRANSFER);
    }

    @Test
    void theFixedContextKeepsTheRatesAndEveryRuleInForceEvenWithZeroAmount() {
        Reservation reservation = born(List.of(line(1, 360_000, 36_000), line(2, 324_000, 0)), 324_000);

        assertThat(reservation.service().baseRatePerPerson()).isEqualTo(new Cents(100_000));
        assertThat(reservation.service().transportRatePerPerson()).isEqualTo(new Cents(20_000));
        assertThat(reservation.fixedDiscounts()).hasSize(2);
        assertThat(reservation.fixedDiscounts().get(1).amount()).isEqualTo(new Cents(0));
        assertThat(reservation.fixedDiscounts().get(0).type()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(reservation.fixedDiscounts().get(0).percentageBasisPoints()).isEqualTo(1000);
    }

    @Test
    void aMissingMandatoryDatumIsNamedAndNothingIsStored() {
        assertThatThrownBy(() -> Reservation.create(ID, TENANT, null, null, CREATOR, 0, null, new Cents(0), new Cents(0),
                List.of(), null, null, NOW))
                .isInstanceOfSatisfying(MissingReservationDataException.class, e -> assertThat(e.fields())
                        .containsExactly("customerId", "salidaId", "serviceId", "people", "paymentType", "paymentMethod"));
    }

    @Test
    void aFinalValueThatIsNotWhatTheDiscountsLeaveIsRefused() {
        assertThatThrownBy(() -> born(List.of(line(1, 360_000, 36_000)), 360_000))
                .isInstanceOf(DomainException.class).extracting("field").isEqualTo("finalValue");
    }

    @Test
    void theProjectedValueIsTheTourPlusTheTransportOfEveryPerson() {
        assertThat(TOUR.projectedFor(3)).isEqualTo(new Cents(360_000));
        assertThat(new ReservedService(SERVICE, new Cents(100_000), null).projectedFor(3)).isEqualTo(new Cents(300_000));
    }

    @Test
    void theExpectedPaymentTypeAndMediumAreStoredAndRejectedWhenUnknown() {
        assertThat(PaymentType.fromCode("FULL_PAYMENT")).isEqualTo(PaymentType.FULL_PAYMENT);
        assertThat(PaymentType.fromCode("DEPOSIT")).isEqualTo(PaymentType.DEPOSIT);
        assertThat(PaymentMethod.fromCode("TRANSFER")).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(PaymentMethod.fromCode("CASH")).isEqualTo(PaymentMethod.CASH);
        assertThatThrownBy(() -> PaymentType.fromCode("CRYPTO")).isInstanceOf(DomainException.class).extracting("field").isEqualTo("paymentType");
        assertThatThrownBy(() -> PaymentMethod.fromCode(null)).isInstanceOf(DomainException.class).extracting("field").isEqualTo("paymentMethod");
    }
}
