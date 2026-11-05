package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-016 over the real schema (api task T-06; AC-16.13, AC-16.14; DP-09): the derived filters of the reads run as SQL on the tables and the indexes of the db,
// scoped to the tenant, in a stable order, and the two lists of job 2 name only what is due. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class RefundQueriesPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private JdbcFinancialDecisionRepository decisions;
    private JdbcRefundAuthorizationRepository refunds;
    private JdbcRefundQueries queries;
    private UUID open, frozen, decided, pendingAuth;
    private UUID overdueRefund, freshRefund;

    @BeforeEach
    void plant() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        decisions = new JdbcFinancialDecisionRepository(fixture.jdbc);
        refunds = new JdbcRefundAuthorizationRepository(fixture.jdbc);
        queries = new JdbcRefundQueries(fixture.jdbc);
        var reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        UUID r1 = UUID.randomUUID(), r2 = UUID.randomUUID(), r3 = UUID.randomUUID();
        for (UUID id : new UUID[] {r1, r2, r3}) reservations.save(fixture.reservation(id, true));
        open = save(r1, DecisionKind.CUSTOMER_CANCELLATION, NOW.minus(Duration.ofHours(30)), false).id();
        frozen = save(r2, DecisionKind.CUSTOMER_CANCELLATION, NOW.minus(Duration.ofHours(2)), true).id();
        FinancialDecision refundFull = save(r3, DecisionKind.CUSTOMER_CANCELLATION, NOW.minus(Duration.ofHours(3)), false).recordRefundFull(fixture.actor, NOW);
        decisions.save(refundFull);
        decided = refundFull.id();
        FinancialDecision other = save(r1, DecisionKind.MODIFICATION_EXCESS, NOW.minus(Duration.ofHours(1)), false).recordRefundFull(fixture.actor, NOW);
        decisions.save(other);
        pendingAuth = other.id();
        RefundAuthorization late = RefundAuthorization.determine(UUID.randomUUID(), fixture.tenant, r1, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, new Cents(9_000),
                NOW.minus(Duration.ofDays(9)), NOW.minus(Duration.ofDays(1)));
        RefundAuthorization fresh = RefundAuthorization.authorize(UUID.randomUUID(), refundFull, "Reason", fixture.actor, NOW, NOW.plus(Duration.ofDays(5)));
        refunds.save(late);
        refunds.save(fresh);
        overdueRefund = late.id();
        freshRefund = fresh.id();
    }

    private FinancialDecision save(UUID reservation, DecisionKind kind, Instant recognizedAt, boolean freeze) {
        FinancialDecision d = FinancialDecision.open(UUID.randomUUID(), fixture.tenant, reservation, kind, new Cents(10_000), null, null, recognizedAt);
        if (freeze) d = d.freeze(NOW.minus(Duration.ofHours(1)));
        decisions.save(d);
        return d;
    }

    @Test
    void pendingAndAttentionAreDerivedAndAnotherTenantSeesNothing() {
        var pending = queries.decisions(fixture.tenant, null, true, false, 1, 50);
        assertThat(pending.items()).extracting(FinancialDecision::id).contains(open, frozen, pendingAuth).doesNotContain(decided);
        var attention = queries.decisions(fixture.tenant, null, false, true, 1, 50);
        assertThat(attention.items()).extracting(FinancialDecision::id).contains(open, pendingAuth).doesNotContain(frozen, decided);
        assertThat(queries.decisions(fixture.tenant, DecisionKind.MODIFICATION_EXCESS, false, false, 1, 50).items()).extracting(FinancialDecision::id)
                .containsExactly(pendingAuth);
        assertThat(queries.decisions(UUID.randomUUID(), null, false, false, 1, 50).total()).isZero();
    }

    @Test
    void theDueListsNameOnlyWhatIsDueAndTheOverdueFilterIgnoresTheExecutedAndTheFresh() {
        assertThat(queries.dueDecisions(fixture.tenant, NOW, 1, 50).items()).extracting(FinancialDecision::id).containsExactly(open);
        assertThat(queries.dueRefunds(fixture.tenant, NOW, 1, 50).items()).extracting(RefundAuthorization::id).containsExactly(overdueRefund);
        var overdue = queries.refunds(fixture.tenant, null, true, NOW, null, 1, 50);
        assertThat(overdue.items()).extracting(RefundAuthorization::id).containsExactly(overdueRefund);
        assertThat(queries.refunds(fixture.tenant, ExecutionState.AUTHORIZED, false, NOW, decided, 1, 50).items()).extracting(RefundAuthorization::id)
                .containsExactly(freshRefund);
        assertThat(queries.dueRefunds(UUID.randomUUID(), NOW, 1, 50).total()).isZero();
    }

    @Test
    void thePageIsBoundedAndTheOrderIsStable() {
        var first = queries.decisions(fixture.tenant, null, false, false, 1, 2);
        var second = queries.decisions(fixture.tenant, null, false, false, 2, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(first.total()).isGreaterThanOrEqualTo(4);
        assertThat(second.items()).extracting(FinancialDecision::id).doesNotContainAnyElementsOf(first.items().stream().map(FinancialDecision::id).toList());
    }
}
