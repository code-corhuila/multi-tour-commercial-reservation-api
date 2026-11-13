package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-016 over HTTP, the three public writes (api spec T-06; AC-16.1 to AC-16.3, AC-16.10, AC-16.12, AC-16.13, AC-16.17, AC-16.18, AC-16.19): the
// Administrator records the 100 % decision, authorizes and rejects; nobody else; no amount travels; every write needs its Idempotency-Key and a repeat answers the
// stored record. Every value is invented.
@Import(RefundHttpFixture.class)
class RefundWriteHttpTest extends ReservationHttpBase {

    private final UUID reservation = new UUID(71, 1);
    @Autowired RefundHttpFixture.MemoryDecisions decisions;
    @Autowired RefundHttpFixture.MemoryRefunds refunds;
    private FinancialDecision cancellation, request, ajena;

    @BeforeEach void plant() {
        decisions.clear();
        refunds.clear();
        cancellation = FinancialDecision.open(new UUID(71, 10), tenant, reservation, DecisionKind.CUSTOMER_CANCELLATION, new Cents(80_000), new Cents(20_000), null,
                NOW.minus(Duration.ofHours(2)));
        request = FinancialDecision.open(new UUID(71, 11), tenant, reservation, DecisionKind.CREDIT_BALANCE_REFUND_REQUEST, new Cents(50_000), null,
                new UUID(71, 90), NOW.minus(Duration.ofHours(2)));
        ajena = FinancialDecision.open(new UUID(71, 12), otherTenant, new UUID(71, 2), DecisionKind.PENDING_TREATMENT, new Cents(1_000), null, null, NOW);
        for (var d : List.of(cancellation, request, ajena)) decisions.decisions.put(d.id(), d);
    }

    private MockHttpServletRequestBuilder write(UUID decision, String route, Map<String, Object> body, String key, String role) throws Exception {
        var request = withToken(post(BASE + "/financial-decisions/" + decision + "/" + route), role).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    @Test void theAdministratorRecordsTheDecisionOnceAndARepeatWithTheSameKeyAnswersTheStoredOne() throws Exception {
        var first = CatalogHttpTest.json(exchange(write(cancellation.id(), "refund-decision", Map.of(), "decision-key-01", ADMIN), 200));
        var again = CatalogHttpTest.json(exchange(write(cancellation.id(), "refund-decision", Map.of(), "decision-key-01", ADMIN), 200));

        assertThat(first.get("outcome").asText()).isEqualTo("REFUND_FULL");
        assertThat(first.get("baseAmountCents").asInt()).isEqualTo(80_000);
        assertThat(first.has("decidedBy")).isTrue();
        assertThat(first.get("decidedBySystem").asBoolean()).isFalse();
        assertThat(again).isEqualTo(first);
        assertThat(refunds.refunds).as("DP-01: the decision is also the authorization").hasSize(1);
        assertThat(refunds.events).hasSize(1);
        assertThat(first.get("refundAuthorizationId").asText()).isEqualTo(refunds.refunds.keySet().iterator().next().toString());
        exchange(write(cancellation.id(), "refund-decision", Map.of(), "decision-key-02", ADMIN), 422);
        exchange(write(request.id(), "refund-decision", Map.of(), "decision-key-03", ADMIN), 422);
    }

    @Test void authorizingCreatesTheRefundOnceWithOneEventAndNoReasonInIt() throws Exception {
        exchange(write(cancellation.id(), "refund-decision", Map.of("reason", "  The customer cancelled in time "), "decision-key-04", ADMIN), 200);

        var first = CatalogHttpTest.json(exchange(write(cancellation.id(), "refund-authorization", Map.of("reason", "  The customer cancelled in time "),
                "authorize-key-01", ADMIN), 201));
        var again = CatalogHttpTest.json(exchange(write(cancellation.id(), "refund-authorization", Map.of("reason", "  The customer cancelled in time "),
                "authorize-key-01", ADMIN), 200));

        assertThat(first.get("authorizedAmountCents").asInt()).isEqualTo(80_000);
        assertThat(first.get("authorizationMode").asText()).isEqualTo("TENANT_ADMINISTRATOR");
        assertThat(first.get("executionState").asText()).isEqualTo("AUTHORIZED");
        assertThat(first.get("reason").asText()).isEqualTo("The customer cancelled in time");
        assertThat(first.get("financialDecisionId").asText()).isEqualTo(cancellation.id().toString());
        assertThat(again).isEqualTo(first);
        assertThat(refunds.refunds).hasSize(1);
        assertThat(refunds.events).hasSize(1);
        exchange(write(cancellation.id(), "refund-authorization", Map.of("reason", "Again"), "authorize-key-02", ADMIN), 201);
        assertThat(refunds.refunds).hasSize(1);
    }

    @Test void theReasonOfTheDecisionIsOptionalAndABlankOneIsRefusedNamingTheField() throws Exception {
        var decided = CatalogHttpTest.json(exchange(write(cancellation.id(), "refund-decision", Map.of(), "decision-key-08", ADMIN), 200));

        assertThat(decided.get("refundAuthorizationId").asText()).isNotBlank();
        assertThat(refunds.refunds.values()).extracting(refund -> refund.reason()).containsExactly("Refund of 100 % decided by the Tenant Administrator");
        CatalogHttpTest.assertField(exchange(write(request.id(), "refund-decision", Map.of("reason", "  "), "decision-key-09", ADMIN), 400), "reason");
    }

    @Test void aDecisionWithoutTheRefundOutcomeCannotBeAuthorizedAndNothingIsWritten() throws Exception {
        exchange(write(cancellation.id(), "refund-authorization", Map.of("reason", "Too soon"), "authorize-key-03", ADMIN), 422);
        assertThat(refunds.refunds).isEmpty();
        assertThat(refunds.events).isEmpty();
    }

    @Test void theAdministratorAuthorizesAnOpenCreditBalanceRequestWithItsRefundInOneAct() throws Exception {
        var refund = CatalogHttpTest.json(exchange(write(request.id(), "refund-authorization", Map.of("reason", "The customer asked for the money back"), "authorize-key-09", ADMIN), 201));

        assertThat(refund.get("authorizedAmountCents").asInt()).isEqualTo(50_000);
        assertThat(refund.get("authorizationMode").asText()).isEqualTo("TENANT_ADMINISTRATOR");
        assertThat(refund.get("financialDecisionId").asText()).isEqualTo(request.id().toString());
        assertThat(decisions.decisions.get(request.id()).outcome().name()).isEqualTo("AUTHORIZED");
        assertThat(decisions.decisions.get(request.id()).decidedBySystem()).isFalse();
        assertThat(refunds.refunds).hasSize(1);
        assertThat(refunds.events).hasSize(1);
        exchange(write(request.id(), "rejection", Map.of("justification", "Too late"), "reject-key-09", ADMIN), 422);
    }

    @Test void aRejectedCreditBalanceRequestCannotBeAuthorized() throws Exception {
        exchange(write(request.id(), "rejection", Map.of("justification", "Not refundable"), "reject-key-10", ADMIN), 200);

        exchange(write(request.id(), "refund-authorization", Map.of("reason", "Again"), "authorize-key-10", ADMIN), 422);
        assertThat(refunds.refunds).isEmpty();
    }

    @Test void onlyACreditBalanceRequestIsRejectedWithItsJustification() throws Exception {
        var rejected = CatalogHttpTest.json(exchange(write(request.id(), "rejection", Map.of("justification", " The balance is not refundable "),
                "reject-key-01", ADMIN), 200));
        var again = CatalogHttpTest.json(exchange(write(request.id(), "rejection", Map.of("justification", " The balance is not refundable "),
                "reject-key-01", ADMIN), 200));

        assertThat(rejected.get("outcome").asText()).isEqualTo("REJECTED");
        assertThat(rejected.get("justification").asText()).isEqualTo("The balance is not refundable");
        assertThat(again).isEqualTo(rejected);
        exchange(write(cancellation.id(), "rejection", Map.of("justification", "No"), "reject-key-02", ADMIN), 422);
    }

    @Test void everyOtherRoleIsRefusedBeforeAnyLoadAndNothingChanges() throws Exception {
        for (String role : List.of(COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER)) {
            exchange(write(cancellation.id(), "refund-decision", Map.of(), "denied-key-" + role.length(), role), 403);
            exchange(write(cancellation.id(), "refund-authorization", Map.of("reason", "Reason"), "denied-key-" + role.length(), role), 403);
            exchange(write(request.id(), "rejection", Map.of("justification", "Reason"), "denied-key-" + role.length(), role), 403);
        }
        assertThat(decisions.decisions.get(cancellation.id())).isEqualTo(cancellation);
        assertThat(decisions.decisions.get(request.id())).isEqualTo(request);
        assertThat(refunds.refunds).isEmpty();
    }

    @Test void anAmountAnEmptyReasonOrAMissingKeyIsRefusedNamingTheFieldAndAnotherTenantLooksMissing() throws Exception {
        exchange(write(cancellation.id(), "refund-decision", Map.of(), "decision-key-05", ADMIN), 200);
        CatalogHttpTest.assertField(exchange(write(cancellation.id(), "refund-authorization", Map.of("reason", "Reason", "amountCents", 5), "authorize-key-04", ADMIN), 400),
                "amountCents");
        CatalogHttpTest.assertField(exchange(write(cancellation.id(), "refund-authorization", Map.of("reason", "  "), "authorize-key-05", ADMIN), 400), "reason");
        CatalogHttpTest.assertField(exchange(write(request.id(), "rejection", Map.of("justification", ""), "reject-key-03", ADMIN), 400), "justification");
        CatalogHttpTest.assertField(exchange(write(cancellation.id(), "refund-decision", Map.of("amountCents", 1), "decision-key-06", ADMIN), 400), "amountCents");
        CatalogHttpTest.assertField(exchange(write(cancellation.id(), "refund-authorization", Map.of("reason", "Reason"), null, ADMIN), 400), "Idempotency-Key");
        exchange(write(ajena.id(), "refund-decision", Map.of(), "decision-key-07", ADMIN), 404);
        exchange(write(ajena.id(), "refund-authorization", Map.of("reason", "Reason"), "authorize-key-06", ADMIN), 404);
        exchange(write(new UUID(71, 99), "rejection", Map.of("justification", "Reason"), "reject-key-04", ADMIN), 404);
        assertThat(refunds.refunds).as("DP-01: only the decision of decision-key-05 wrote a refund; every refusal wrote none").hasSize(1);
    }

    @Test void aKeyAlreadyUsedByAnotherRequestIsRefused() throws Exception {
        exchange(write(cancellation.id(), "refund-decision", Map.of(), "shared-key-001", ADMIN), 200);

        exchange(write(request.id(), "rejection", Map.of("justification", "Reason"), "shared-key-001", ADMIN), 422);
        assertThat(decisions.decisions.get(request.id())).isEqualTo(request);
    }
}
