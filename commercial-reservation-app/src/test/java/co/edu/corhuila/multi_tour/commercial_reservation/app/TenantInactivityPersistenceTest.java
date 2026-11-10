package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusStore;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantSuspensions;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ApplyTenantEventUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.TenantInactivityUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SuspensionIntervals;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEventType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-020 over the real schema (api tasks T-02, T-04; AC-20.2, AC-20.3, AC-20.4, AC-20.6, AC-20.15, AC-20.16, AC-20.19; db slice tenant_suspension and freeze_cause): the inactivation of one tenant
// opens its suspension and freezes only its deadlines with the remaining time and the cause, the reactivation closes it once and resumes what the inactivation froze, a deadline frozen by the
// cancelled departure is left alone, a repeat of the event changes nothing, and a second tenant is never touched. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class TenantInactivityPersistenceTest {
    private static final Instant T0 = Instant.parse("2026-10-29T14:00:00Z");

    private ReservationPersistenceFixture a;
    private ReservationPersistenceFixture b;
    private ApplyTenantEventUseCase apply;
    private JdbcTenantSuspensions suspensions;
    private UUID reservationA;
    private UUID reservationB;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        a = new ReservationPersistenceFixture();
        b = new ReservationPersistenceFixture();
        var jdbc = a.jdbc;
        suspensions = new JdbcTenantSuspensions(jdbc);
        var effects = new TenantInactivityUseCase(suspensions, new JdbcTenantDeadlines(jdbc), new UuidGenerator());
        apply = new ApplyTenantEventUseCase(new JdbcTenantStatusStore(jdbc), new JdbcProcessedEvents(jdbc), new JdbcTenantEventTransaction(a.transactions), effects);
        reservationA = reservation(a);
        reservationB = reservation(b);
        apply.apply(event(TenantEventType.TENANT_CREATED, a.tenant, T0.minusSeconds(3600), "ta-" + a.tenant.toString().substring(0, 8)));
        apply.apply(event(TenantEventType.TENANT_CREATED, b.tenant, T0.minusSeconds(3600), "tb-" + b.tenant.toString().substring(0, 8)));
    }

    private UUID reservation(ReservationPersistenceFixture fixture) {
        UUID id = UUID.randomUUID();
        new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc)).save(fixture.reservation(id, true));
        deadline(fixture, id, "INITIAL_PAYMENT", T0.plus(Duration.ofHours(5)));
        deadline(fixture, id, "BALANCE", T0.plus(Duration.ofHours(50)));
        return id;
    }

    private void deadline(ReservationPersistenceFixture fixture, UUID reservation, String kind, Instant due) {
        fixture.jdbc.update("INSERT INTO commercial_reservation.payment_deadline (payment_deadline_id, tenant_id, reservation_id, deadline_kind, due_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), fixture.tenant, reservation, kind, OffsetDateTime.ofInstant(due, ZoneOffset.UTC));
    }

    private TenantEvent event(TenantEventType type, UUID tenant, Instant at, String identifier) {
        boolean created = type == TenantEventType.TENANT_CREATED;
        return new TenantEvent(UUID.randomUUID(), type, tenant, at, created ? identifier : null, created ? TenantStatus.ACTIVE : null, created ? "America/Bogota" : null, created ? "CO" : null);
    }

    private Object[] row(ReservationPersistenceFixture fixture, UUID reservation, String kind) {
        return a.jdbc.queryForObject("SELECT frozen_at, remaining_at_freeze_minutes, freeze_cause, due_at FROM commercial_reservation.payment_deadline WHERE tenant_id = ? AND reservation_id = ? AND deadline_kind = ?",
                (r, i) -> new Object[] {r.getObject("frozen_at", OffsetDateTime.class), r.getObject("remaining_at_freeze_minutes"), r.getString("freeze_cause"), r.getObject("due_at", OffsetDateTime.class)},
                fixture.tenant, reservation, kind);
    }

    @Test
    void theInactivationFreezesOnlyTheDeadlinesOfThatTenantWithTheRemainingTimeAndTheCause() {
        apply.apply(event(TenantEventType.TENANT_INACTIVATED, a.tenant, T0, null));

        Object[] initial = row(a, reservationA, "INITIAL_PAYMENT");
        assertThat(((OffsetDateTime) initial[0]).toInstant()).isEqualTo(T0);
        assertThat(initial[1]).isEqualTo(300);
        assertThat(initial[2]).isEqualTo("TENANT_INACTIVE");
        assertThat(row(a, reservationA, "BALANCE")[1]).isEqualTo(3000);
        assertThat(row(b, reservationB, "INITIAL_PAYMENT")[0]).as("the other tenant is never touched").isNull();
        assertThat(a.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.tenant_suspension WHERE tenant_id = ?", Integer.class, b.tenant)).isZero();
    }

    @Test
    void theReactivationClosesTheSuspensionOnceAndResumesWhatTheInactivationFroze() {
        apply.apply(event(TenantEventType.TENANT_INACTIVATED, a.tenant, T0, null));
        Instant back = T0.plus(Duration.ofHours(30));

        apply.apply(event(TenantEventType.TENANT_REACTIVATED, a.tenant, back, null));

        Object[] initial = row(a, reservationA, "INITIAL_PAYMENT");
        assertThat(initial[0]).isNull();
        assertThat(initial[2]).isNull();
        assertThat(((OffsetDateTime) initial[3]).toInstant()).isEqualTo(back.plus(Duration.ofHours(5)));
        assertThat(a.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.tenant_suspension WHERE tenant_id = ? AND resumed_at IS NOT NULL", Integer.class, a.tenant)).isEqualTo(1);
        assertThat(suspensions.intervals(a.tenant).intervals()).singleElement().satisfies(interval -> {
            assertThat(interval.suspendedAt()).isEqualTo(T0);
            assertThat(interval.resumedAt()).isEqualTo(back);
        });
    }

    @Test
    void aRepeatOfTheEventAndADeadlineFrozenByTheDepartureChangeNothing() {
        a.jdbc.update("UPDATE commercial_reservation.payment_deadline SET frozen_at = ?, remaining_at_freeze_minutes = 40, freeze_cause = 'DEPARTURE_CANCELLED_OR_INTERRUPTED' WHERE tenant_id = ? AND reservation_id = ? AND deadline_kind = 'BALANCE'",
                OffsetDateTime.ofInstant(T0.minusSeconds(600), ZoneOffset.UTC), a.tenant, reservationA);
        TenantEvent inactivated = event(TenantEventType.TENANT_INACTIVATED, a.tenant, T0, null);

        apply.apply(inactivated);
        apply.apply(inactivated);
        apply.apply(event(TenantEventType.TENANT_REACTIVATED, a.tenant, T0.plus(Duration.ofHours(30)), null));

        assertThat(a.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.tenant_suspension WHERE tenant_id = ?", Integer.class, a.tenant)).isEqualTo(1);
        Object[] balance = row(a, reservationA, "BALANCE");
        assertThat(balance[1]).isEqualTo(40);
        assertThat(balance[2]).isEqualTo("DEPARTURE_CANCELLED_OR_INTERRUPTED");
        assertThat(suspensions.intervals(b.tenant)).isEqualTo(new SuspensionIntervals(java.util.List.of()));
    }
}
