package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BasisPoints;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-004 api plan section 2, AC-04.3, AC-04.4, AC-04.12: the current breakdown of a reservation reads the reservation as
// it was created and the stored additional discount; it never recalculates the fixed lines and it writes nothing.
class GetReservationDiscountBreakdownUseCaseTest {
    private static final UUID TENANT = new UUID(8, 1), OTHER = new UUID(8, 2), ACCOUNT = new UUID(8, 3),
            RESERVATION = new UUID(8, 7), AUTHOR = new UUID(8, 9);
    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    private Reservation reservation = reservation(new Cents(200_000));
    private final AdditionalDiscountFakes fakes = new AdditionalDiscountFakes();
    private boolean ownerIsTheAccount = true;

    private final GetReservationDiscountBreakdownUseCase useCase = new GetReservationDiscountBreakdownUseCase(
            (tenant, id) -> found(tenant, id), (tenant, account, id) -> account.equals(ACCOUNT) && ownerIsTheAccount
                    ? found(tenant, id) : fail(id),
            fakes);

    private Reservation found(UUID tenant, UUID id) {
        if (!reservation.tenantId().equals(tenant) || !reservation.reservationId().equals(id)) return fail(id);
        return reservation;
    }

    private static Reservation fail(UUID id) { throw new ReservationNotFoundException(id); }

    private static Reservation reservation(Cents value) {
        return Reservation.create(RESERVATION, TENANT, new UUID(8, 8), new UUID(8, 6), AUTHOR, 2,
                new ReservedService(new UUID(8, 4), new Cents(100_000), null), value, value, List.of(), PaymentType.DEPOSIT,
                PaymentMethod.CASH, NOW);
    }

    private AdditionalDiscount tenPercent() {
        var rate = new BasisPoints(1_000);
        return new AdditionalDiscount(TENANT, RESERVATION, rate, DiscountLine.percentage(new Cents(200_000), rate),
                "Loyal customer", AUTHOR, 1, NOW);
    }

    @Test void withoutAnAdditionalDiscountOnlyTheFixedBreakdownIsReturned() {
        var view = useCase.get(TENANT, RESERVATION);
        assertThat(view.reservation().finalValue()).isEqualTo(new Cents(200_000));
        assertThat(view.additional()).isEmpty();
        assertThat(view.excess()).isEqualTo(new Cents(0));
    }

    @Test void theStoredAdditionalDiscountIsReturnedWithItsAuthorAndDate() {
        fakes.save(tenPercent());
        var view = useCase.get(TENANT, RESERVATION);
        assertThat(view.additional()).get().satisfies(found -> {
            assertThat(found.authorizerId()).isEqualTo(AUTHOR);
            assertThat(found.registeredAt()).isEqualTo(NOW);
            assertThat(found.line().subtotal()).isEqualTo(new Cents(180_000));
        });
        assertThat(view.reservation().finalValue()).isEqualTo(new Cents(200_000));
    }

    @Test void theExcessIsWhatWasValidatedAboveTheNewValue() {
        fakes.save(tenPercent());
        reservation = reservation(new Cents(200_000)).restoredAs(ReservationStatus.CONFIRMED, PaymentStatus.PARTIAL,
                new Cents(10_000), true, List.of());
        assertThat(useCase.get(TENANT, RESERVATION).excess()).isEqualTo(new Cents(10_000));
    }

    @Test void nothingPaidAboveTheNewValueLeavesNoExcess() {
        fakes.save(tenPercent());
        reservation = reservation(new Cents(200_000)).restoredAs(ReservationStatus.CONFIRMED, PaymentStatus.PARTIAL,
                new Cents(150_000), true, List.of());
        assertThat(useCase.get(TENANT, RESERVATION).excess()).isEqualTo(new Cents(0));
    }

    @Test void aReservationOfAnotherTenantOrAMissingOneIsNotFound() {
        assertThatThrownBy(() -> useCase.get(OTHER, RESERVATION)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> useCase.get(TENANT, new UUID(8, 99))).isInstanceOf(ReservationNotFoundException.class);
    }

    @Test void anAdditionalDiscountOfAnotherTenantIsNeverShown() {
        fakes.save(new AdditionalDiscount(OTHER, RESERVATION, new BasisPoints(1_000),
                DiscountLine.percentage(new Cents(200_000), new BasisPoints(1_000)), "x", AUTHOR, 1, NOW));
        assertThat(useCase.get(TENANT, RESERVATION).additional()).isEmpty();
    }

    @Test void aFinalCustomerReadsOnlyTheirOwnReservation() {
        fakes.save(tenPercent());
        assertThat(useCase.getOwn(TENANT, ACCOUNT, RESERVATION).additional()).isPresent();
        assertThatThrownBy(() -> useCase.getOwn(TENANT, new UUID(8, 55), RESERVATION))
                .isInstanceOf(ReservationNotFoundException.class);
        ownerIsTheAccount = false;
        assertThatThrownBy(() -> useCase.getOwn(TENANT, ACCOUNT, RESERVATION)).isInstanceOf(ReservationNotFoundException.class);
    }
}
