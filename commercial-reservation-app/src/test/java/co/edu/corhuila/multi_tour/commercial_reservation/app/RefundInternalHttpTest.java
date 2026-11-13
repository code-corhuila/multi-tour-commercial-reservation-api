package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExecuteRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ExecuteRefundUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-016 over HTTP, the internal routes (api spec T-05; AC-16.4 to AC-16.9, AC-16.11, AC-16.13, AC-16.17, AC-16.19): the steps of the saga, the default
// and the overdue mark of job 2 and the determination of operations, for the service token only, with the tenant of the path. The saga and the job ask and
// the service decides. Every value is invented.
@Import({RefundHttpFixture.class, RefundInternalHttpTest.ExecuteRefundWithoutNotice.class})
class RefundInternalHttpTest extends ReservationHttpBase {

    // The completion step writes the REFUND_PROCESSED notice (HU-COM-015) through a JDBC outbox that the configuration builds inline, so over memory it would open the
    // fictitious database and answer 500. These routes run over the memory fakes: the use case is built here without the notice, which job 4 and its own tests cover.
    @TestConfiguration
    static class ExecuteRefundWithoutNotice {
        @Bean @Primary ExecuteRefund memoryExecuteRefund(RefundHttpFixture.MemoryRefunds refunds, PaymentTransaction transaction, Clock clock) {
            return new ExecuteRefundUseCase(refunds, transaction, clock);
        }
    }

    private final UUID reservationId = new UUID(51, 1);
    private final UUID person = new UUID(51, 2), movement = new UUID(51, 3);
    @Autowired RefundHttpFixture.MemoryDecisions decisions;
    @Autowired RefundHttpFixture.MemoryRefunds refunds;
    @Autowired RefundHttpFixture.MemorySnapshots snapshots;
    private String service;
    private RefundAuthorization refund;

    @BeforeEach void plant() throws Exception {
        decisions.clear();
        refunds.clear();
        snapshots.snapshots.clear();
        service = CustomerHttpTest.token(null, "SERVICE", List.of());
        refund = RefundAuthorization.determine(new UUID(51, 10), tenant, reservationId, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, new Cents(100_000),
                NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(4)));
        refunds.refunds.put(refund.id(), refund);
        snapshots.snapshots.put(reservationId, new ReservationSnapshot(tenant, reservationId, ReservationSnapshot.State.CANCELLED, new Cents(300_000), new Cents(300_000), 0));
    }

    private MockHttpServletRequestBuilder step(UUID forTenant, UUID id, String step, Map<String, Object> body, String bearer) throws Exception {
        return post(BASE + "/internal/tenants/" + forTenant + "/refund-authorizations/" + id + "/" + step)
                .header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
    }

    private MockHttpServletRequestBuilder defaulting(UUID id, String bearer) throws Exception {
        return post(BASE + "/internal/tenants/" + tenant + "/financial-decisions/" + id + "/default")
                .header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", TRACE);
    }

    private Map<String, Object> start() {
        return Map.of("executedBy", person.toString(), "outflowMethod", "TRANSFER");
    }

    @Test void theSagaStartsUndoesAndCompletesTheRefundAndEachStepRepeatedAnswersTheSame() throws Exception {
        var started = CatalogHttpTest.json(exchange(step(tenant, refund.id(), "execution-start", start(), service), 200));
        var again = CatalogHttpTest.json(exchange(step(tenant, refund.id(), "execution-start", start(), service), 200));
        assertThat(started.get("executionState").asText()).isEqualTo("EXECUTING");
        assertThat(started.get("executedBy").asText()).isEqualTo(person.toString());
        assertThat(started.get("outflowMethod").asText()).isEqualTo("TRANSFER");
        assertThat(again).isEqualTo(started);

        var undone = CatalogHttpTest.json(exchange(step(tenant, refund.id(), "execution-undo", Map.of(), service), 200));
        assertThat(undone.get("executionState").asText()).isEqualTo("AUTHORIZED");
        assertThat(undone.has("executedBy")).isFalse();

        exchange(step(tenant, refund.id(), "execution-start", start(), service), 200);
        var done = CatalogHttpTest.json(exchange(step(tenant, refund.id(), "execution-completion", Map.of("cashMovementId", movement.toString()), service), 200));
        assertThat(done.get("executionState").asText()).isEqualTo("EXECUTED");
        assertThat(done.get("cashMovementId").asText()).isEqualTo(movement.toString());
        assertThat(CatalogHttpTest.json(exchange(step(tenant, refund.id(), "execution-completion", Map.of("cashMovementId", movement.toString()), service), 200)))
                .isEqualTo(done);
    }

    @Test void anExecutedRefundIsTerminalAndAnotherPersonOrMovementIsRefusedWithNothingChanged() throws Exception {
        exchange(step(tenant, refund.id(), "execution-start", start(), service), 200);
        exchange(step(tenant, refund.id(), "execution-start", Map.of("executedBy", new UUID(51, 9).toString(), "outflowMethod", "TRANSFER"), service), 422);
        exchange(step(tenant, refund.id(), "execution-completion", Map.of("cashMovementId", movement.toString()), service), 200);

        exchange(step(tenant, refund.id(), "execution-completion", Map.of("cashMovementId", new UUID(51, 8).toString()), service), 422);
        exchange(step(tenant, refund.id(), "execution-undo", Map.of(), service), 422);
        assertThat(refunds.refunds.get(refund.id()).cashMovementId()).isEqualTo(movement);
    }

    @Test void theStepsAreForTheServiceTokenOnlyNameTheFieldsAndAnotherTenantLooksMissing() throws Exception {
        exchange(step(tenant, refund.id(), "execution-start", start(), token(ADMIN)), 403);
        exchange(step(tenant, refund.id(), "execution-start", start(), token(COLLABORATOR)), 403);
        CatalogHttpTest.assertField(exchange(step(tenant, refund.id(), "execution-start", Map.of("executedBy", person.toString()), service), 400), "outflowMethod");
        CatalogHttpTest.assertField(exchange(step(tenant, refund.id(), "execution-completion", Map.of(), service), 400), "cashMovementId");
        exchange(step(otherTenant, refund.id(), "execution-start", start(), service), 404);
        exchange(step(tenant, new UUID(51, 99), "execution-undo", Map.of(), service), 404);
        assertThat(refunds.refunds.get(refund.id())).isEqualTo(refund);
    }

    @Test void theDefaultAuthorizesADueDecisionOnceAndRefusesOneThatIsNotDue() throws Exception {
        FinancialDecision due = FinancialDecision.open(new UUID(51, 20), tenant, reservationId, DecisionKind.CUSTOMER_CANCELLATION, new Cents(80_000),
                new Cents(20_000), null, NOW.minus(Duration.ofHours(25)));
        FinancialDecision fresh = FinancialDecision.open(new UUID(51, 21), tenant, reservationId, DecisionKind.PENDING_TREATMENT, new Cents(5_000), null,
                null, NOW.minus(Duration.ofHours(1)));
        decisions.decisions.put(due.id(), due);
        decisions.decisions.put(fresh.id(), fresh);

        var made = CatalogHttpTest.json(exchange(defaulting(due.id(), service), 200));

        assertThat(made.get("authorizationMode").asText()).isEqualTo("AUTOMATIC_DETERMINATION");
        assertThat(made.get("authorizedAmountCents").asInt()).isEqualTo(80_000);
        assertThat(made.has("authorizedBy")).isFalse();
        assertThat(refunds.events).hasSize(1);
        exchange(defaulting(fresh.id(), service), 422);
        exchange(defaulting(due.id(), token(ADMIN)), 403);
    }

    @Test void theOverdueMarkIsRefusedWhileNotDueAndKeepsTheRefundPendingWhenDue() throws Exception {
        exchange(step(tenant, refund.id(), "overdue-mark", Map.of(), service), 422);
        RefundAuthorization overdue = RefundAuthorization.determine(new UUID(51, 11), tenant, reservationId, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER,
                new Cents(10_000), NOW.minus(Duration.ofDays(8)), NOW.minus(Duration.ofDays(1)));
        refunds.refunds.put(overdue.id(), overdue);

        var marked = CatalogHttpTest.json(exchange(step(tenant, overdue.id(), "overdue-mark", Map.of(), service), 200));

        assertThat(marked.get("overdue").asBoolean()).isTrue();
        assertThat(marked.get("executionState").asText()).isEqualTo("AUTHORIZED");
    }

    private MockHttpServletRequestBuilder determination(UUID forTenant, Map<String, Object> body, String key, String bearer) throws Exception {
        var request = post(BASE + "/internal/tenants/" + forTenant + "/refund-determinations").header("Authorization", "Bearer " + bearer)
                .header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    @Test void aDeterminationIsAuthorizedAtOnceOnceAndARepeatWithTheSameKeyAnswersTheStoredRefund() throws Exception {
        refunds.refunds.clear();
        Map<String, Object> body = Map.of("reservationId", reservationId.toString(), "origin", "NOT_ATTRIBUTABLE_TO_CUSTOMER");

        var first = CatalogHttpTest.json(exchange(determination(tenant, body, "determination-01", service), 201));
        var again = CatalogHttpTest.json(exchange(determination(tenant, body, "determination-01", service), 200));

        assertThat(first.get("authorizedAmountCents").asInt()).isEqualTo(300_000);
        assertThat(first.get("origin").asText()).isEqualTo("NOT_ATTRIBUTABLE_TO_CUSTOMER");
        assertThat(again).isEqualTo(first);
        assertThat(refunds.refunds).hasSize(1);
        assertThat(refunds.events).hasSize(1);
    }

    @Test void aDeterminationNeedsItsKeyTheServiceTokenAndTheRightAmountField() throws Exception {
        Map<String, Object> body = Map.of("reservationId", reservationId.toString(), "origin", "NOT_ATTRIBUTABLE_TO_CUSTOMER");
        CatalogHttpTest.assertField(exchange(determination(tenant, body, null, service), 400), "Idempotency-Key");
        exchange(determination(tenant, body, "determination-02", token(ADMIN)), 403);
        CatalogHttpTest.assertField(exchange(determination(tenant, Map.of("reservationId", reservationId.toString(), "origin", "NOT_ATTRIBUTABLE_TO_CUSTOMER",
                "amountCents", 5), "determination-03", service), 400), "amountCents");
        CatalogHttpTest.assertField(exchange(determination(tenant, Map.of("reservationId", reservationId.toString(), "origin", "PERSONAL_EMERGENCY_DURING_EXECUTION"),
                "determination-04", service), 400), "amountCents");
        exchange(determination(otherTenant, body, "determination-05", service), 404);
        assertThat(refunds.events).isEmpty();
    }
}
