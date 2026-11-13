package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.calendar.WeekdayBusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPendingDifferenceDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.FinancialDecisionOpening;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ModifyReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-012 DD-4 (api spec, register of 2026-11-12): the excess of a modification reaches the financial decision of HU-COM-016 through the REAL opener
// (FinancialDecisionOpening over the JDBC repositories, the adapter ModificationConfiguration wires) on PostgreSQL, not through a double. The opener runs inside the
// transaction of the modification, so a failure after it leaves the reservation, the seats, the history and the decision as they were. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ModificationExcessOpeningPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW.plusSeconds(3600);
    private static final Cents MINIMUM = new Cents(97_200);
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository reservations;
    private JdbcReservationModifications history;
    private FinancialDecisionOpening opening;
    private ModifyReservationUseCase modify;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        history = new JdbcReservationModifications(fixture.jdbc);
        opening = new FinancialDecisionOpening(new JdbcFinancialDecisionRepository(fixture.jdbc), new JdbcRefundAuthorizationRepository(fixture.jdbc),
                new WeekdayBusinessCalendar(), new JdbcRefundAuthorizedOutbox(fixture.jdbc), new UuidGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));
        OffsetDateTime start = OffsetDateTime.ofInstant(ReservationPersistenceFixture.NOW, ZoneOffset.UTC).plusDays(10);
        fixture.jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, "
                + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 3)", UUID.randomUUID(), fixture.tenant,
                fixture.salida, fixture.service, start);
        fixture.jdbc.update("INSERT INTO commercial_reservation.service_change_policy (service_change_policy_id, tenant_id, service_id, change_type, "
                + "change_allowed, request_lead_minutes, changed_by, changed_at) VALUES (?, ?, ?, ?, true, 60, ?, ?)", UUID.randomUUID(), fixture.tenant,
                fixture.service, "MODIFICATION", fixture.actor, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        modify = modifyWith(opening);
    }

    private ModifyReservationUseCase modifyWith(ExcessDecisionOpener excess) {
        return new ModifyReservationUseCase(reservations, new JdbcDepartureReader(fixture.jdbc), new JdbcChangePolicyReader(fixture.jdbc),
                new JdbcPaymentConditionReader(fixture.jdbc), new JdbcSalidaCapacityHolder(fixture.jdbc), excess, new JdbcPendingDifferenceDeadlines(fixture.jdbc),
                history, new JdbcPaymentEventOutbox(fixture.jdbc), new JdbcPaymentTransaction(fixture.transactions), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // Three travellers paid in cash for the whole final value, so a decrease leaves a validated value above the new final value.
    private Reservation paidInFull() {
        UUID id = UUID.randomUUID();
        // A reservation is born PENDING_PAYMENT (trg_reservation_birth): store it as born, then confirm it with the cash and store it again.
        reservations.save(fixture.reservation(id, true, fixture.plainCompanion()));
        Reservation reservation = reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
        reservation.registerCash(UUID.randomUUID(), reservation.finalValue(), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        reservation.allocateSeat(ReservationPersistenceFixture.NOW);
        reservations.save(reservation);
        return reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
    }

    private PaymentActor actor() {
        return new PaymentActor(fixture.tenant, fixture.actor, Role.OPERATIONAL_COLLABORATOR);
    }

    private int decisionRows(UUID reservation) {
        return fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.financial_decision WHERE reservation_id = ?", Integer.class, reservation);
    }

    @Test
    void theExcessOfADecreaseOpensOneModificationExcessDecisionWithTheRealOpener() {
        UUID id = paidInFull().reservationId();

        var record = modify.modify(actor(), id, new ModificationRequest(ChangeType.MODIFICATION, 2, fixture.salida, List.of()), "One traveller less");

        long excess = record.outcome().excess().amount();
        assertThat(excess).isPositive();
        assertThat(decisionRows(id)).isEqualTo(1);
        var row = fixture.jdbc.queryForMap("SELECT decision_kind, base_amount_cents, penalty_amount_cents, outcome FROM commercial_reservation.financial_decision "
                + "WHERE tenant_id = ? AND reservation_id = ?", fixture.tenant, id);
        assertThat(row.get("decision_kind")).isEqualTo("MODIFICATION_EXCESS");
        assertThat(((Number) row.get("base_amount_cents")).longValue()).isEqualTo(excess);
        assertThat(row.get("penalty_amount_cents")).isNull();
        assertThat(row.get("outcome")).as("a decision opens undecided").isNull();
        assertThat(history.page(fixture.tenant, id, 1, 20).total()).isEqualTo(1);
    }

    @Test
    void aModificationWithNoExcessOpensNoDecision() {
        UUID id = paidInFull().reservationId();

        modify.modify(actor(), id, new ModificationRequest(ChangeType.MODIFICATION, 4, fixture.salida, List.of(fixture.plainCompanion())), "One traveller more");

        assertThat(decisionRows(id)).isZero();
        assertThat(history.page(fixture.tenant, id, 1, 20).total()).isEqualTo(1);
    }

    @Test
    void aFailureAfterTheOpenerRollsTheWholeModificationBackDecisionIncluded() {
        UUID id = paidInFull().reservationId();
        var failing = modifyWith((tenant, reservationId, excess, at) -> {
            opening.open(tenant, reservationId, excess, at);
            throw new IllegalStateException("the decision could not be completed");
        });

        assertThatThrownBy(() -> failing.modify(actor(), id, new ModificationRequest(ChangeType.MODIFICATION, 2, fixture.salida, List.of()), "One traveller less"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(decisionRows(id)).as("the decision written by the opener went back with the transaction").isZero();
        assertThat(history.page(fixture.tenant, id, 1, 20).total()).isZero();
        assertThat(reservations.findByTenantAndId(fixture.tenant, id).orElseThrow().people()).isEqualTo(3);
        assertThat(fixture.jdbc.queryForObject("SELECT held_seats FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?", Integer.class,
                fixture.tenant, fixture.salida)).isEqualTo(3);
    }

    @Test
    void aSecondDecreaseWhileTheDecisionIsOpenOpensNoSecondDecision() {
        UUID id = paidInFull().reservationId();
        modify.modify(actor(), id, new ModificationRequest(ChangeType.MODIFICATION, 2, fixture.salida, List.of()), "One traveller less");

        modify.modify(actor(), id, new ModificationRequest(ChangeType.MODIFICATION, 1, fixture.salida, List.of()), "Another traveller less");

        assertThat(decisionRows(id)).isEqualTo(1);
        assertThat(history.page(fixture.tenant, id, 1, 20).total()).isEqualTo(2);
    }
}
