package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentQuoteQuery;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterTransferCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-07 of HU-COM-009, the core part: RN-03; AC-09.3, AC-09.15. The service computes and the portal only shows. The JDBC
// readers that serve 008 and 010 (EnabledPaymentOptions, PaymentConditionReader, PaymentDeadlineReader) are adapter work.
class GetPaymentQuoteTest {

    private static final UUID TENANT = new UUID(9, 0xd1), ACTOR = new UUID(9, 0xd3), CUSTOMER = new UUID(9, 0xd4),
            SERVICE = new UUID(9, 0xd5), DEPARTURE = new UUID(9, 0xd6), RESERVATION = new UUID(9, 0xd7);
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1};

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final Set<String> enabled = new HashSet<>(Set.of("FULL_PAYMENT:TRANSFER", "DEPOSIT:TRANSFER", "DEPOSIT:CASH"));
    private final GetPaymentQuoteUseCase quote = new GetPaymentQuoteUseCase(fakes, (tenant, service, type, method) -> enabled.contains(type + ":" + method),
            fakes, fakes);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    GetPaymentQuoteTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 0));
        // Two people, 300.000, expected type deposit; the fake answers a minimum of 100.000.
        fakes.reservations.put(RESERVATION, Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ACTOR, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW));
    }

    @Test
    void theQuoteOffersOnlyEnabledTypesAndMethodsWithTheAmountTheMinimumAndTheBalance() {
        PaymentQuote result = quote.quote(new PaymentQuoteQuery(TENANT, RESERVATION, null));

        assertThat(result.enabledTypes()).containsExactlyInAnyOrder(PaymentType.FULL_PAYMENT, PaymentType.DEPOSIT);
        assertThat(result.enabledMethods()).containsExactlyInAnyOrder(PaymentMethod.TRANSFER, PaymentMethod.CASH);
        assertThat(result.paymentType()).isEqualTo(PaymentType.DEPOSIT);
        assertThat(result.amountToPay()).isEqualTo(new Cents(100_000));
        assertThat(result.minimumDeposit()).isEqualTo(new Cents(100_000));
        assertThat(result.pendingBalance()).isEqualTo(new Cents(300_000));
    }

    @Test
    void aDisabledTypeIsNotOfferedAndAFullPaymentQuotesTheWholeBalanceWithNoMinimum() {
        enabled.remove("DEPOSIT:TRANSFER");
        enabled.remove("DEPOSIT:CASH");

        PaymentQuote result = quote.quote(new PaymentQuoteQuery(TENANT, RESERVATION, PaymentType.FULL_PAYMENT));

        assertThat(result.enabledTypes()).containsExactly(PaymentType.FULL_PAYMENT);
        assertThat(result.enabledMethods()).containsExactly(PaymentMethod.TRANSFER);
        assertThat(result.amountToPay()).isEqualTo(new Cents(300_000));
        assertThat(result.minimumDeposit()).isNull();
    }

    @Test
    void aTenantWithoutParameterRowsGetsAnEmptyQuote() {
        enabled.clear();

        PaymentQuote result = quote.quote(new PaymentQuoteQuery(TENANT, RESERVATION, null));

        assertThat(result.enabledTypes()).isEmpty();
        assertThat(result.enabledMethods()).isEmpty();
        assertThat(result.amountToPay()).isNull();
        assertThat(result.pendingBalance()).isEqualTo(new Cents(300_000));
    }

    @Test
    void afterADepositTheQuoteIsTheRemainingBalanceAndTheDeadlineIsServed() {
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.plusSeconds(3_600)));
        new RegisterTransferUseCase(fakes, departures, fakes, (t, s, ty, m) -> true, fakes, fakes, fakes, fakes, fakes, UUID::randomUUID, clock)
                .register(new RegisterTransferCommand(TENANT, ACTOR, RESERVATION, 100_000L, PNG));
        fakes.reservations.put(RESERVATION, fakes.reservations.get(RESERVATION).restoredAs(
                co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus.CONFIRMED,
                co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus.DEPOSIT_RECEIVED, new Cents(200_000), true, List.of()));

        PaymentQuote result = quote.quote(new PaymentQuoteQuery(TENANT, RESERVATION, null));

        assertThat(result.amountToPay()).isEqualTo(new Cents(200_000));
        assertThat(result.pendingBalance()).isEqualTo(new Cents(200_000));
        assertThat(result.paymentDeadline()).isEqualTo(NOW.plusSeconds(3_600));
    }

    @Test
    void theDeclaredValueIsStoredAsDeclaredAndNeverReplacesTheAmountToPay() {
        PaymentQuote before = quote.quote(new PaymentQuoteQuery(TENANT, RESERVATION, null));

        new RegisterTransferUseCase(fakes, departures, fakes, (t, s, ty, m) -> true, fakes, fakes, fakes, fakes, fakes, UUID::randomUUID, clock)
                .register(new RegisterTransferCommand(TENANT, ACTOR, RESERVATION, 120_000L, PNG));

        assertThat(fakes.reservations.get(RESERVATION).attempts().get(0).amount()).isEqualTo(new Cents(120_000));
        assertThat(before.amountToPay()).isEqualTo(new Cents(100_000));
        assertThat(quote.quote(new PaymentQuoteQuery(TENANT, RESERVATION, null)).amountToPay()).isEqualTo(new Cents(100_000));
    }

    @Test
    void aReservationOfAnotherTenantOrMissingAnswersNotFound() {
        assertThatThrownBy(() -> quote.quote(new PaymentQuoteQuery(new UUID(9, 0xee), RESERVATION, null)))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> quote.quote(new PaymentQuoteQuery(TENANT, new UUID(9, 0xff), null)))
                .isInstanceOf(ReservationNotFoundException.class);
    }
}
