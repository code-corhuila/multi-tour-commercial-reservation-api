package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-05 of HU-COM-020 (-api): RN-07; AC-20.7, AC-20.8. With the tenant inactive, the restricted Administrator may register the voluntary cancellation request of a customer only while the departure is
// still scheduled and its start has not arrived: the normal policy and settlement apply and the 24-hour decision is opened frozen with its 24 hours intact until the reactivation. Otherwise the request
// is refused with a business rule violation (422) and nothing changes. With the tenant active nothing changes. The resume of that decision is declared debt (the db keeps no shifted due instant).
class CancelWhileInactiveTest {

    private static final UUID TENANT = new UUID(20, 0xa1), ADMIN = new UUID(20, 0xa2), CUSTOMER = new UUID(20, 0xa3), SERVICE = new UUID(20, 0xa4), DEPARTURE = new UUID(20, 0xa5),
            RESERVATION = new UUID(20, 0xa6);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant NOW = CREATED.plus(Duration.ofDays(2));
    private static final Instant START = CREATED.plus(Duration.ofDays(30));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final CancellationFakes cancellation = new CancellationFakes();
    private final List<String> opened = new ArrayList<>();
    private final FinancialDecisionOpener decisions = new FinancialDecisionOpener() {
        @Override public void open(UUID tenantId, UUID reservationId, Cents base, Cents penalty, Instant recognizedAt) { opened.add("open:" + base.amount()); }
        @Override public void openFrozen(UUID tenantId, UUID reservationId, Cents base, Cents penalty, Instant recognizedAt) { opened.add("frozen:" + base.amount() + "@" + recognizedAt); }
    };
    private Optional<TenantStatus> status = Optional.of(TenantStatus.INACTIVE);
    private final CancelReservationUseCase cancel = new CancelReservationUseCase(fakes, departures, fakes, cancellation, cancellation, decisions, cancellation, fakes, fakes,
            Clock.fixed(NOW, ZoneOffset.UTC), tenantId -> status);

    CancelWhileInactiveTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, START, DepartureStatus.SCHEDULED, 10, 2));
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(300_000), new Cents(300_000), List.of(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, CREATED);
        reservation.restoredAs(ReservationStatus.CONFIRMED, PaymentStatus.PARTIAL, new Cents(100_000), false, List.of());
        fakes.reservations.put(RESERVATION, reservation);
    }

    private CancelReservationCommand command() {
        return new CancelReservationCommand(new PaymentActor(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), RESERVATION, "The customer cannot travel", "CUSTOMER_REQUEST");
    }

    @Test
    void aVoluntaryRequestOnAScheduledDepartureAppliesThePolicyAndOpensTheDecisionFrozen() {
        cancel.cancel(command());

        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(fakes.reservations.get(RESERVATION).cancellationCause()).isEqualTo(CancellationCause.CUSTOMER_REQUEST);
        assertThat(opened).containsExactly("frozen:200000@" + NOW);
    }

    @Test
    void aVoluntaryRequestOnAReachedOrNotScheduledDepartureIsRefused() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.minusSeconds(60), DepartureStatus.SCHEDULED, 10, 2));
        assertThatThrownBy(() -> cancel.cancel(command())).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("not attributable");

        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, START, DepartureStatus.CANCELLED, 10, 2));
        assertThatThrownBy(() -> cancel.cancel(command())).isInstanceOf(BusinessRuleViolation.class);

        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, START, DepartureStatus.IN_EXECUTION, 10, 2));
        assertThatThrownBy(() -> cancel.cancel(command())).isInstanceOf(BusinessRuleViolation.class);

        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(fakes.saved).isEmpty();
        assertThat(opened).isEmpty();
    }

    @Test
    void anActiveOrUnknownTenantKeepsTheOrdinaryRunningDecision() {
        status = Optional.of(TenantStatus.ACTIVE);
        cancel.cancel(command());
        assertThat(opened).containsExactly("open:200000");
    }
}
