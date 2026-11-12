package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangePaymentParametersCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterCashCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameterValues;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-08 of HU-COM-009, the core part: RN-14; AC-09.17 (decided by default, F3). A change of the payment parameters applies to a reservation at
// the moment of payment: the registration reads the enabled methods and the minimum deposit from the same rows the Administrator changes,
// when it runs, and the reservation keeps no copy of them. The registration is the real use case; only its ports are doubles.
class PaymentParametersAtThePaymentMomentTest {

    private static final UUID TENANT = new UUID(9, 0xa1), ADMIN = new UUID(9, 0xa2), CUSTOMER = new UUID(9, 0xa4), SERVICE = new UUID(9, 0xa5),
            DEPARTURE = new UUID(9, 0xa6), RESERVATION = new UUID(9, 0xa7);
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final CashFakes cash = new CashFakes();
    private final List<PaymentParameter> rows = new ArrayList<>();
    private final PaymentParameterRepository repository = new PaymentParameterRepository() {
        @Override public List<PaymentParameter> findByTenant(UUID tenantId) {
            return List.copyOf(rows);
        }
        @Override public void save(UUID tenantId, PaymentParameter parameter) {
            rows.removeIf(row -> row.values().paymentType() == parameter.values().paymentType());
            rows.add(parameter);
        }
    };
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ManagePaymentParametersUseCase manage = new ManagePaymentParametersUseCase(repository, fakes, change -> { }, clock);
    // The two readers of the registration, answering from the rows as they are when the payment is registered.
    private final PaymentConditionReader conditions = (tenant, reservation) -> rows.stream()
            .filter(row -> row.values().paymentType() == PaymentType.DEPOSIT)
            .map(row -> new Cents((reservation.finalValue().amount() * row.values().minimumDepositBp() + 9_999) / 10_000)).findFirst();
    private final RegisterCashPaymentUseCase register = new RegisterCashPaymentUseCase(fakes, departures, fakes,
            (tenant, service, type, method) -> rows.stream().anyMatch(row -> row.values().paymentType() == type
                    && (method == PaymentMethod.CASH ? row.values().cashEnabled() : row.values().transferEnabled())),
            conditions, fakes, fakes, fakes, cash, cash, cash, UUID::randomUUID, clock);

    PaymentParametersAtThePaymentMomentTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 0));
        fakes.reservations.put(RESERVATION, Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.CASH, NOW));
    }

    private void change(int minimumBp, boolean cashEnabled) {
        manage.change(new ChangePaymentParametersCommand(new PaymentActor(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), "new season",
                List.of(new PaymentParameterValues(PaymentType.DEPOSIT, null, 24, 24, minimumBp, 48, true, cashEnabled))));
    }

    private void payCash(long amount) {
        register.register(new RegisterCashCommand(TENANT, ADMIN, RESERVATION, amount));
    }

    @Test
    void changedParametersApplyAtThePaymentMoment() {
        change(3_000, true);
        change(5_000, true);

        // The reservation was created before the last change and is paid after it: the new minimum (150.000) applies, not the old one (90.000).
        payCash(90_000L);
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);

        // The next payment meets the minimum that is in force then (90.000 after the second change), with no copy kept on the reservation.
        change(3_000, true);
        payCash(1_000L);
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void aMethodTheAdministratorDisablesIsRefusedAtOnceAndAcceptedAgainWhenEnabled() {
        change(3_000, false);
        assertThatThrownBy(() -> payCash(90_000L))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("paymentMethod"));
        assertThat(cash.income).isEmpty();

        change(3_000, true);
        payCash(90_000L);
        assertThat(cash.income).hasSize(1);
    }
}
