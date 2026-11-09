package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRetentionFactsReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.retention.DeferredReservationClosing;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RetentionFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// T-02 of HU-COM-019 (-api): AC-19.2; plan D-03. The facts that decide the anchor are read from the departure mirror, the reservation and its decisions, scoped to the
// tenant; a reservation whose departure has no end on the mirror reads as unknown facts, never as an error; the closing of HU-COM-016 and HU-COM-017 answers empty
// until those stories give it (declared debt DD-2). Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class RetentionFactsPersistenceTest {
    private static final Instant ESTIMATED_END = Instant.parse("2026-03-10T15:00:00Z");
    private static final Instant FINALIZED_AT = Instant.parse("2026-03-10T18:30:00Z");
    private ReservationPersistenceFixture fixture;
    private JdbcRetentionFactsReader facts;
    private JdbcReservationRepository reservations;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        facts = new JdbcRetentionFactsReader(fixture.jdbc);
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        reservations.save(fixture.reservation(reservation, true));
    }

    private final UUID reservation = UUID.randomUUID();

    private void mirror(String status, Instant estimatedEnd, Instant finalizedAt) {
        fixture.jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, hora_inicio, status, "
                + "effective_capacity, held_seats, estimated_ends_at, finalized_at) VALUES (?, ?, ?, ?, ?, ?, 10, 0, ?, ?)", UUID.randomUUID(), fixture.tenant,
                fixture.salida, fixture.service, OffsetDateTime.ofInstant(ESTIMATED_END.minusSeconds(3600), ZoneOffset.UTC), status,
                estimatedEnd == null ? null : OffsetDateTime.ofInstant(estimatedEnd, ZoneOffset.UTC),
                finalizedAt == null ? null : OffsetDateTime.ofInstant(finalizedAt, ZoneOffset.UTC));
    }

    @Test
    void theFactsOfAFinalizedCancelledAndUnregularizedReservationAreRead() {
        mirror("FINALIZED", ESTIMATED_END, FINALIZED_AT);
        RetentionFacts finalized = facts.factsOf(fixture.tenant, reservation).orElseThrow();
        assertThat(finalized.customerId()).isEqualTo(fixture.customer);
        assertThat(finalized.departure().status()).isEqualTo(DepartureStatus.FINALIZED);
        assertThat(finalized.departure().estimatedEndsAt()).isEqualTo(ESTIMATED_END);
        assertThat(finalized.departure().finalizedAt()).isEqualTo(FINALIZED_AT);
        assertThat(finalized.reservation().status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(finalized.reservation().cancelledAt()).isNull();

        fixture.jdbc.update("UPDATE commercial_reservation.salida_capacity SET status = 'IN_EXECUTION', finalized_at = NULL WHERE salida_id = ?", fixture.salida);
        RetentionFacts open = facts.factsOf(fixture.tenant, reservation).orElseThrow();
        assertThat(open.departure().status()).isEqualTo(DepartureStatus.IN_EXECUTION);
        assertThat(open.departure().finalizedAt()).isNull();

        fixture.jdbc.update("UPDATE commercial_reservation.reservation SET reservation_status = 'CANCELLED', cancellation_cause = 'CUSTOMER_REQUEST', "
                + "cancelled_at = ? WHERE reservation_id = ?", OffsetDateTime.ofInstant(ESTIMATED_END, ZoneOffset.UTC), reservation);
        RetentionFacts cancelled = facts.factsOf(fixture.tenant, reservation).orElseThrow();
        assertThat(cancelled.reservation().status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.reservation().cancelledAt()).isEqualTo(ESTIMATED_END);
        assertThat(cancelled.reservation().nothingToTreat()).isTrue();

        fixture.jdbc.update("INSERT INTO commercial_reservation.financial_decision (financial_decision_id, tenant_id, reservation_id, decision_kind, "
                + "base_amount_cents, penalty_amount_cents, recognized_at) VALUES (?, ?, ?, 'CUSTOMER_CANCELLATION', 80000, 0, ?)", UUID.randomUUID(),
                fixture.tenant, reservation, OffsetDateTime.ofInstant(ESTIMATED_END, ZoneOffset.UTC));
        assertThat(facts.factsOf(fixture.tenant, reservation).orElseThrow().reservation().nothingToTreat()).isFalse();
    }

    @Test
    void aDepartureWithoutAnEndOnTheMirrorReadsAsUnknownAndAnotherTenantSeesNothing() {
        RetentionFacts none = facts.factsOf(fixture.tenant, reservation).orElseThrow();
        assertThat(none.departure().status()).isNull();
        assertThat(none.departure().estimatedEndsAt()).isNull();

        mirror("SCHEDULED", null, null);
        assertThat(facts.factsOf(fixture.tenant, reservation).orElseThrow().departure().estimatedEndsAt()).isNull();
        assertThat(facts.factsOf(UUID.randomUUID(), reservation)).isEmpty();
        assertThat(facts.factsOf(fixture.tenant, UUID.randomUUID())).isEmpty();
    }

    @Test
    void theClosingAnswersEmptyUntilTheCreditAndRefundStoriesGiveIt() {
        assertThat(new DeferredReservationClosing().closingOf(fixture.tenant, reservation)).isEmpty();
    }
}
