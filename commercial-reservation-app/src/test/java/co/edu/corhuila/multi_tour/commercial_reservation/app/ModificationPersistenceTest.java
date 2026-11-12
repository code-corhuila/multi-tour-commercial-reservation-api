package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPendingDifferenceDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ModifyReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-012 over the real schema (api T-04 to T-08; AC-12.1 to AC-12.6, AC-12.11, AC-12.15): the use case with the real adapters stores the
// recomputed reservation (party, value, line amounts, companions, seat) the way the engine allows, writes the history row and the closed
// event, moves the seats and reads the terms from the table. The excess decision is a double. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ModificationPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW.plusSeconds(3600);
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository reservations;
    private JdbcReservationModifications history;
    private ModifyReservationUseCase modify;
    private final UUID otherSalida = UUID.randomUUID();
    private final List<Long> excesses = new ArrayList<>();

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        history = new JdbcReservationModifications(fixture.jdbc);
        OffsetDateTime start = OffsetDateTime.ofInstant(ReservationPersistenceFixture.NOW, ZoneOffset.UTC).plusDays(10);
        for (UUID salida : List.of(fixture.salida, otherSalida))
            fixture.jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, "
                    + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, ?)", UUID.randomUUID(),
                    fixture.tenant, salida, fixture.service, start, salida.equals(fixture.salida) ? 3 : 0);
        fixture.jdbc.update("INSERT INTO commercial_reservation.service_change_policy (service_change_policy_id, tenant_id, service_id, change_type, "
                + "change_allowed, request_lead_minutes, changed_by, changed_at) VALUES (?, ?, ?, ?, true, 60, ?, ?)", UUID.randomUUID(), fixture.tenant,
                fixture.service, "MODIFICATION", fixture.actor, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        modify = new ModifyReservationUseCase(reservations, new JdbcDepartureReader(fixture.jdbc), new JdbcChangePolicyReader(fixture.jdbc),
                new JdbcPaymentConditionReader(fixture.jdbc), new JdbcSalidaCapacityHolder(fixture.jdbc),
                (tenant, reservation, excess, at) -> excesses.add(excess.amount()), new JdbcPendingDifferenceDeadlines(fixture.jdbc), history,
                new JdbcPaymentEventOutbox(fixture.jdbc), new JdbcPaymentTransaction(fixture.transactions), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Reservation stored(boolean held) {
        UUID id = UUID.randomUUID();
        Reservation reservation = fixture.reservation(id, true, fixture.plainCompanion());
        if (held) reservation.allocateSeat(ReservationPersistenceFixture.NOW);
        reservations.save(reservation);
        return reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
    }

    private PaymentActor actor() {
        return new PaymentActor(fixture.tenant, fixture.actor, Role.OPERATIONAL_COLLABORATOR);
    }

    private int held(UUID salida) {
        return fixture.jdbc.queryForObject("SELECT held_seats FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?",
                Integer.class, fixture.tenant, salida);
    }

    @Test
    void anIncreaseStoresTheRecomputedReservationTheHistoryAndTheClosedEvent() {
        Reservation reservation = stored(true);
        UUID id = reservation.reservationId();

        var record = modify.modify(actor(), id, new ModificationRequest(ChangeType.MODIFICATION, 4, fixture.salida,
                List.of(fixture.plainCompanion())), "  The customer adds a traveller  ");

        Reservation again = reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
        assertThat(again.people()).isEqualTo(4);
        assertThat(again.projectedValue()).isEqualTo(new Cents(480_000));
        assertThat(again.finalValue()).isEqualTo(record.outcome().newFinalValue());
        assertThat(again.fixedDiscounts().get(0).amount()).isEqualTo(new Cents(48_000));
        assertThat(again.seatHeld()).isTrue();
        assertThat(fixture.jdbc.queryForObject("SELECT party_size FROM commercial_reservation.capacity_allocation WHERE reservation_id = ? "
                + "AND released_at IS NULL", Integer.class, id)).isEqualTo(4);
        assertThat(held(fixture.salida)).isEqualTo(4);
        var page = history.page(fixture.tenant, id, 1, 20);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().get(0).reason()).isEqualTo("The customer adds a traveller");
        assertThat(page.items().get(0).previousPartySize()).isEqualTo(3);
        assertThat(history.page(UUID.randomUUID(), id, 1, 20).total()).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'ReservationModified' AND payload ->> 'changeType' = 'MODIFICATION' AND NOT jsonb_exists(payload, 'reason')",
                Integer.class, id)).isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'CapacityAllocated'", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void aDecreaseReplacesTheCompanionsAndGivesTheSeatsBack() {
        Reservation reservation = stored(true);
        UUID id = reservation.reservationId();

        modify.modify(actor(), id, new ModificationRequest(ChangeType.MODIFICATION, 2, fixture.salida, List.of()), "One traveller less");

        Reservation again = reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
        assertThat(again.people()).isEqualTo(2);
        assertThat(again.companions()).isEmpty();
        assertThat(fixture.count("reservation_companion", id)).isZero();
        assertThat(held(fixture.salida)).isEqualTo(2);
        assertThat(fixture.jdbc.queryForObject("SELECT party_size FROM commercial_reservation.reserved_service WHERE reservation_id = ?",
                Integer.class, id)).isEqualTo(2);
    }

    @Test
    void aDateChangeMovesTheSeatAndTheDepartureOfTheReservation() {
        Reservation reservation = stored(true);
        UUID id = reservation.reservationId();
        fixture.jdbc.update("INSERT INTO commercial_reservation.service_change_policy (service_change_policy_id, tenant_id, service_id, change_type, "
                + "change_allowed, request_lead_minutes, changed_by, changed_at) VALUES (?, ?, ?, ?, true, 60, ?, ?)", UUID.randomUUID(), fixture.tenant,
                fixture.service, "DATE_CHANGE", fixture.actor, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));

        modify.modify(actor(), id, new ModificationRequest(ChangeType.DATE_CHANGE, 3, otherSalida, List.of(fixture.plainCompanion())), "Another date");

        assertThat(reservations.findByTenantAndId(fixture.tenant, id).orElseThrow().departureId()).isEqualTo(otherSalida);
        assertThat(held(fixture.salida)).isEqualTo(0);
        assertThat(held(otherSalida)).isEqualTo(3);
        assertThat(fixture.jdbc.queryForObject("SELECT salida_id FROM commercial_reservation.capacity_allocation WHERE reservation_id = ? "
                + "AND released_at IS NULL", UUID.class, id)).isEqualTo(otherSalida);
    }

    @Test
    void withoutAPolicyRowTheChangeIsRefusedAndNothingIsWritten() {
        Reservation reservation = stored(true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> modify.modify(actor(), reservation.reservationId(),
                new ModificationRequest(ChangeType.DATE_CHANGE, 3, otherSalida, List.of(fixture.plainCompanion())), "No terms"))
                .isInstanceOf(co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation.class);

        assertThat(history.page(fixture.tenant, reservation.reservationId(), 1, 20).total()).isZero();
        assertThat(held(fixture.salida)).isEqualTo(3);
    }
}
