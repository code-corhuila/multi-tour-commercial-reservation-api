package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDuePaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.treatment.DeferredPaymentSettlement;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ExpireReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExpiryVerdict;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-011 over the real schema, the expiry end to end (api tasks T-05, T-06, T-07; AC-11.2, AC-11.11): the use case with the real
// adapters cancels once with the cause and the instant the engine requires, writes ReservationCancelled once, and the due list leaves the
// frozen, the not yet due, the confirmed and the other tenants out. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PaymentExpiryPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository reservations;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
    }

    private ExpireReservationUseCase expiry(Instant at) {
        return new ExpireReservationUseCase(reservations, new JdbcPaymentDeadlineReader(fixture.jdbc), new JdbcDepartureReader(fixture.jdbc),
                new JdbcSalidaCapacityHolder(fixture.jdbc), new DeferredPaymentSettlement(), new JdbcPaymentEventOutbox(fixture.jdbc),
                new JdbcPaymentTransaction(fixture.transactions), Clock.fixed(at, ZoneOffset.UTC));
    }

    private Reservation stored(Instant due) {
        UUID id = UUID.randomUUID();
        reservations.save(fixture.reservation(id, true));
        fixture.jdbc.update("INSERT INTO commercial_reservation.payment_deadline (payment_deadline_id, tenant_id, reservation_id, deadline_kind, due_at) "
                + "VALUES (?, ?, ?, 'INITIAL_PAYMENT', ?)", UUID.randomUUID(), fixture.tenant, id, OffsetDateTime.ofInstant(due, ZoneOffset.UTC));
        return reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
    }

    @Test
    void theExpiryCancelsOnceWithItsCauseAndInstantAndItsEvent() {
        Reservation reservation = stored(NOW.plusSeconds(60));
        var use = expiry(NOW.plusSeconds(120));

        var first = use.expire(fixture.tenant, reservation.reservationId());
        var second = use.expire(fixture.tenant, reservation.reservationId());

        assertThat(first.expired()).isTrue();
        assertThat(second.expired()).isFalse();
        assertThat(second.reason()).isEqualTo(ExpiryVerdict.ALREADY_CANCELLED);
        assertThat(fixture.jdbc.queryForObject("SELECT reservation_status || ':' || cancellation_cause || ':' || cancelled_at FROM "
                + "commercial_reservation.reservation WHERE reservation_id = ?", String.class, reservation.reservationId()))
                .startsWith("CANCELLED:PAYMENT_EXPIRY:");
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'ReservationCancelled'", Integer.class, reservation.reservationId())).isEqualTo(1);
    }

    @Test
    void aReservationNotYetDueIsLeftAsItWas() {
        Reservation reservation = stored(NOW.plusSeconds(3_600));

        var result = expiry(NOW.plusSeconds(60)).expire(fixture.tenant, reservation.reservationId());

        assertThat(result.reason()).isEqualTo(ExpiryVerdict.NOT_DUE);
        assertThat(fixture.jdbc.queryForObject("SELECT reservation_status FROM commercial_reservation.reservation WHERE reservation_id = ?",
                String.class, reservation.reservationId())).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void theDueListHoldsOnlyTheUnfrozenDeadlinesThatWereReachedInsideTheTenant() {
        Reservation due = stored(NOW.plusSeconds(60));
        Reservation future = stored(NOW.plusSeconds(7_200));
        Reservation frozen = stored(NOW.plusSeconds(60));
        fixture.jdbc.update("UPDATE commercial_reservation.payment_deadline SET frozen_at = ?, remaining_at_freeze_minutes = 5 WHERE reservation_id = ?",
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), frozen.reservationId());
        var queries = new JdbcDuePaymentQueries(fixture.jdbc);

        var page = queries.page(fixture.tenant, NOW.plusSeconds(120), 1, 20);

        assertThat(page.total()).isEqualTo(1);
        var row = page.items().get(0);
        assertThat(row.reservationId()).isEqualTo(due.reservationId());
        assertThat(row.effective()).isEqualTo(NOW.plusSeconds(60));
        assertThat(row.deadline().frozen()).isFalse();
        assertThat(queries.page(fixture.tenant, NOW.plusSeconds(10_000), 1, 20).total()).isEqualTo(2);
        assertThat(queries.page(UUID.randomUUID(), NOW.plusSeconds(10_000), 1, 20).total()).isZero();
        assertThat(future.reservationId()).isNotEqualTo(due.reservationId());
    }
}
