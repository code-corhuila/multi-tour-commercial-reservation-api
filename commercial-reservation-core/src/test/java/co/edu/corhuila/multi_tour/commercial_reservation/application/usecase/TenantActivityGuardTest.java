package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueDeadlinePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExpiryResult;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DuePaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExpiryVerdict;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
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

// T-01 of HU-COM-020 (-api): RN-01; AC-20.1, AC-20.19. While the copy says INACTIVE no due list returns a row and no expiry cancels: the expiry answers expired=false with the reason TENANT_INACTIVE and
// changes nothing, and no cancellation for non-payment exists. The guard reads the status at the call (never a cached one); an unknown tenant is active; a failed read is thrown, never answered as a
// value. The persistence adapters and the routes are unchanged: the use cases only gain the status reader.
class TenantActivityGuardTest {

    private static final UUID TENANT = new UUID(20, 1), ADMIN = new UUID(20, 2), CUSTOMER = new UUID(20, 3), SERVICE = new UUID(20, 4), DEPARTURE = new UUID(20, 5), RESERVATION = new UUID(20, 6);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant DUE = CREATED.plus(Duration.ofHours(24));
    private static final Instant NOW = DUE.plusSeconds(60);

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final ExpiryFakes expiry = new ExpiryFakes(fakes);
    private Optional<TenantStatus> status = Optional.of(TenantStatus.INACTIVE);
    private final List<String> reads = new ArrayList<>();
    private final TenantStatusReader statuses = tenantId -> { reads.add("status:" + tenantId); return status; };
    private final ExpireReservationUseCase expire = new ExpireReservationUseCase(fakes, fakes, departures, fakes, expiry, fakes, fakes, Clock.fixed(NOW, ZoneOffset.UTC), statuses);
    private final List<String> asked = new ArrayList<>();
    private final DuePaymentQueries queries = (tenantId, now, page, limit) -> { asked.add(tenantId + ":" + now); return new DueDeadlinePage(List.of(), 1); };
    private final ListDuePaymentDeadlinesUseCase list = new ListDuePaymentDeadlinesUseCase(queries, Clock.fixed(NOW, ZoneOffset.UTC), statuses);

    TenantActivityGuardTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, DUE.plus(Duration.ofDays(30)), DepartureStatus.SCHEDULED, 10, 2));
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(300_000), new Cents(300_000), List.of(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, CREATED);
        fakes.reservations.put(RESERVATION, reservation);
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE));
    }

    @Test
    void aDueListOfAnInactiveTenantIsEmpty() {
        DueDeadlinePage page = list.list(TENANT, 1, 50);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(asked).as("the query of the deadlines is not even asked").isEmpty();
    }

    @Test
    void anExpiryOfAnInactiveTenantIsRefusedWithTheReason() {
        ExpiryResult result = expire.expire(TENANT, RESERVATION);

        assertThat(result.expired()).isFalse();
        assertThat(result.reason()).isEqualTo(ExpiryVerdict.TENANT_INACTIVE);
        assertThat(result.cause()).isNull();
        assertThat(result.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    void noCancellationForExpiryExistsWhileTheTenantIsInactive() {
        expire.expire(TENANT, RESERVATION);
        expire.expire(TENANT, RESERVATION);

        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.saved).isEmpty();
        assertThat(reads).hasSize(2);
    }

    @Test
    void anActiveOrUnknownTenantKeepsItsBehaviour() {
        status = Optional.of(TenantStatus.ACTIVE);
        assertThat(list.list(TENANT, 1, 50).total()).isEqualTo(1);
        assertThat(expire.expire(TENANT, RESERVATION).expired()).isTrue();

        status = Optional.empty();
        assertThat(list.list(TENANT, 1, 50).total()).as("an unknown tenant is active").isEqualTo(1);
    }

    @Test
    void aFailedReadIsThrownNeverAnsweredAsAValue() {
        TenantStatusReader broken = tenantId -> { throw new IllegalStateException("the copy cannot be read"); };

        assertThatThrownBy(() -> new ListDuePaymentDeadlinesUseCase(queries, Clock.fixed(NOW, ZoneOffset.UTC), broken).list(TENANT, 1, 50)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ExpireReservationUseCase(fakes, fakes, departures, fakes, expiry, fakes, fakes, Clock.fixed(NOW, ZoneOffset.UTC), broken).expire(TENANT, RESERVATION))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }
}
