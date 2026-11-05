package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.OutflowMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-016 over HTTP, the reads (api spec T-06; AC-16.13, AC-16.14; DP-04, DP-09): the decisions and the refunds for the Administrator and the Collaborator
// only, whole and never editable, and the two lists of job 2 for the service token with the tenant of the path. Every value is invented.
@Import(RefundHttpFixture.class)
class RefundReadHttpTest extends ReservationHttpBase {

    private final UUID reservation = new UUID(61, 1);
    @Autowired RefundHttpFixture.MemoryDecisions decisions;
    @Autowired RefundHttpFixture.MemoryRefunds refunds;
    private FinancialDecision open, frozen, decided, ajena;
    private RefundAuthorization refund, overdue;
    private String service;

    @BeforeEach void plant() throws Exception {
        decisions.clear();
        refunds.clear();
        service = CustomerHttpTest.token(null, "SERVICE", List.of());
        open = FinancialDecision.open(new UUID(61, 10), tenant, reservation, DecisionKind.CUSTOMER_CANCELLATION, new Cents(80_000), new Cents(20_000), null,
                NOW.minus(Duration.ofHours(30)));
        frozen = FinancialDecision.open(new UUID(61, 11), tenant, reservation, DecisionKind.PENDING_TREATMENT, new Cents(5_000), null, null,
                NOW.minus(Duration.ofHours(2))).freeze(NOW.minus(Duration.ofHours(1)));
        decided = FinancialDecision.open(new UUID(61, 12), tenant, reservation, DecisionKind.MODIFICATION_EXCESS, new Cents(7_000), null, null,
                NOW.minus(Duration.ofHours(3))).recordRefundFull(new UUID(61, 99), NOW.minus(Duration.ofHours(2)));
        ajena = FinancialDecision.open(new UUID(61, 13), otherTenant, new UUID(61, 2), DecisionKind.PENDING_TREATMENT, new Cents(1_000), null, null, NOW);
        for (var d : List.of(open, frozen, decided, ajena)) decisions.decisions.put(d.id(), d);
        refund = RefundAuthorization.authorize(new UUID(61, 20), decided, "Private reason", new UUID(61, 99), NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(5)));
        overdue = RefundAuthorization.determine(new UUID(61, 21), tenant, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, new Cents(9_000),
                NOW.minus(Duration.ofDays(8)), NOW.minus(Duration.ofDays(1))).startExecution(new UUID(61, 98), OutflowMethod.CASH, NOW.minus(Duration.ofDays(2)));
        refunds.refunds.put(refund.id(), refund);
        refunds.refunds.put(overdue.id(), overdue);
    }

    private MockHttpServletRequestBuilder read(String path, String bearer) {
        return get(BASE + path).header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", TRACE);
    }

    @Test void theAdministratorAndTheCollaboratorReadTheDecisionsOfTheirTenantOnly() throws Exception {
        for (String role : List.of(ADMIN, COLLABORATOR)) {
            var page = CatalogHttpTest.json(exchange(read("/financial-decisions", token(role)), 200));
            assertThat(page.at("/meta/total").asInt()).isEqualTo(3);
            assertThat(page.get("data")).extracting(n -> n.get("financialDecisionId").asText())
                    .doesNotContain(ajena.id().toString()).contains(open.id().toString());
        }
        var one = CatalogHttpTest.json(exchange(read("/financial-decisions/" + open.id(), token(COLLABORATOR)), 200));
        assertThat(one.get("kind").asText()).isEqualTo("CUSTOMER_CANCELLATION");
        assertThat(one.get("baseAmountCents").asInt()).isEqualTo(80_000);
        assertThat(one.get("penaltyCents").asInt()).isEqualTo(20_000);
        assertThat(one.get("frozen").asBoolean()).isFalse();
        assertThat(one.get("decisionDueAt").asText()).isEqualTo(open.dueAt().toString());
        assertThat(one.has("outcome")).isFalse();
        exchange(read("/financial-decisions/" + ajena.id(), token(ADMIN)), 404);
    }

    @Test void thePendingAndAttentionFiltersAreDerivedAndTheKindFilterIsClosed() throws Exception {
        refunds.refunds.remove(refund.id()); // the decided one has no refund yet: it is pending authorization
        var pending = CatalogHttpTest.json(exchange(read("/financial-decisions?pending=true", token(ADMIN)), 200));
        assertThat(pending.get("data")).extracting(n -> n.get("financialDecisionId").asText())
                .containsExactlyInAnyOrder(open.id().toString(), frozen.id().toString(), decided.id().toString());
        refunds.refunds.put(refund.id(), refund);
        var attention = CatalogHttpTest.json(exchange(read("/financial-decisions?attention=true", token(ADMIN)), 200));
        assertThat(attention.get("data")).extracting(n -> n.get("financialDecisionId").asText()).containsExactly(open.id().toString());
        var kind = CatalogHttpTest.json(exchange(read("/financial-decisions?kind=PENDING_TREATMENT", token(ADMIN)), 200));
        assertThat(kind.at("/meta/total").asInt()).isEqualTo(1);
        CatalogHttpTest.assertField(exchange(read("/financial-decisions?kind=OTHER", token(ADMIN)), 400), "kind");
        CatalogHttpTest.assertField(exchange(read("/financial-decisions?limit=500", token(ADMIN)), 400), "limit");
    }

    @Test void theRefundsShowTheWholeRecordWithItsStateTheOverdueFilterAndNoEditableField() throws Exception {
        var all = CatalogHttpTest.json(exchange(read("/refund-authorizations", token(COLLABORATOR)), 200));
        assertThat(all.at("/meta/total").asInt()).isEqualTo(2);
        var late = CatalogHttpTest.json(exchange(read("/refund-authorizations?overdue=true&executionState=EXECUTING", token(ADMIN)), 200));
        assertThat(late.get("data")).hasSize(1);
        assertThat(late.at("/data/0/refundAuthorizationId").asText()).isEqualTo(overdue.id().toString());
        var one = CatalogHttpTest.json(exchange(read("/refund-authorizations/" + refund.id(), token(ADMIN)), 200));
        assertThat(one.get("reason").asText()).isEqualTo("Private reason");
        assertThat(one.get("authorizationMode").asText()).isEqualTo("TENANT_ADMINISTRATOR");
        assertThat(one.get("executionDueAt").asText()).isEqualTo(refund.executionDueAt().toString());
        assertThat(one.get("overdue").asBoolean()).isFalse();
        var byDecision = CatalogHttpTest.json(exchange(read("/refund-authorizations?financialDecisionId=" + decided.id(), token(ADMIN)), 200));
        assertThat(byDecision.at("/meta/total").asInt()).isEqualTo(1);
        exchange(read("/refund-authorizations/" + new UUID(61, 77), token(ADMIN)), 404);
    }

    @Test void everyOtherRoleIsRefusedBeforeAnyLoadAndTheRoutesAreReadOnly() throws Exception {
        for (String role : List.of(MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER)) {
            exchange(read("/financial-decisions", token(role)), 403);
            exchange(read("/financial-decisions/" + open.id(), token(role)), 403);
            exchange(read("/refund-authorizations", token(role)), 403);
            exchange(read("/refund-authorizations/" + refund.id(), token(role)), 403);
        }
        exchange(read("/financial-decisions", service), 403);
        exchange(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(BASE + "/refund-authorizations/" + refund.id())
                .header("Authorization", "Bearer " + token(ADMIN)).header("X-Correlation-Id", TRACE), 404);
    }

    @Test void theListsOfJobTwoAreForTheServiceTokenAndNameWhatIsDueWithoutDeciding() throws Exception {
        var decisionsDue = CatalogHttpTest.json(exchange(read("/internal/tenants/" + tenant + "/financial-decisions?due=true", service), 200));
        assertThat(decisionsDue.get("data")).extracting(n -> n.get("financialDecisionId").asText()).containsExactly(open.id().toString());
        var refundsDue = CatalogHttpTest.json(exchange(read("/internal/tenants/" + tenant + "/refund-authorizations?due=true", service), 200));
        assertThat(refundsDue.get("data")).extracting(n -> n.get("refundAuthorizationId").asText()).containsExactly(overdue.id().toString());
        assertThat(CatalogHttpTest.json(exchange(read("/internal/tenants/" + otherTenant + "/refund-authorizations?due=true", service), 200)).at("/meta/total").asInt())
                .isZero();
        exchange(read("/internal/tenants/" + tenant + "/financial-decisions?due=true", token(ADMIN)), 403);
        CatalogHttpTest.assertField(exchange(read("/internal/tenants/" + tenant + "/financial-decisions", service), 400), "due");
        CatalogHttpTest.assertField(exchange(read("/internal/tenants/" + tenant + "/refund-authorizations?due=false", service), 400), "due");
    }
}
