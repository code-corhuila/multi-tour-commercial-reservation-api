package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Audit M1 (HU-COM-017 AC-17.13, AC-17.14, AC-17.16, DP-05) over HTTP with a second linked customer and a second tenant, without a database: the list and the detail never
// expose the credit of another customer, and applying or asking a refund of a credit that is not theirs is refused to a Final Customer, who owns the credit when it is theirs.
@Import({CreditHttpFixture.class, RefundHttpFixture.class})
class CreditOwnershipHttpTest extends ReservationHttpBase {

    private static final UUID OWNER_ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT), SECOND_ACCOUNT = new UUID(0, 0x5A1), UNLINKED_ACCOUNT = new UUID(0, 0x5A9),
            SECOND_CUSTOMER = new UUID(1, 0x5A2), FOREIGN_CUSTOMER = new UUID(1, 0x5A3), OWNER_CREDIT = new UUID(1, 0x5B1), SECOND_CREDIT = new UUID(1, 0x5B2);
    @Autowired CreditHttpFixture.MemoryCredits credits;
    @Autowired RefundHttpFixture.MemoryDecisions decisions;
    private String ownerSource, ownerTarget, secondSource;

    @BeforeEach void plant() throws Exception {
        credits.clear();
        decisions.clear();
        customers.save(customers.customers.get(customer).linkedTo(OWNER_ACCOUNT));
        customers.save(Customer.registerLead(SECOND_CUSTOMER, tenant, "CC2222", "Segunda Persona", BirthDateOrAge.ofAge(41),
                new ContactInfo("segunda@example.invalid", "3000000002"), true, NOW).linkedTo(SECOND_ACCOUNT));
        // The account of the first customer is also linked in another tenant: the tenant of the token decides what it can see.
        directory.tenants.put(otherTenant.toString(), new TenantRef(otherTenant, true));
        customers.save(Customer.registerLead(FOREIGN_CUSTOMER, otherTenant, "CC3333", "Persona de Otro Tenant", BirthDateOrAge.ofAge(35),
                new ContactInfo("ajena@example.invalid", "3000000003"), true, NOW).linkedTo(OWNER_ACCOUNT));
        ownerSource = reservationOf(customer, "plant-key-0001");
        ownerTarget = reservationOf(customer, "plant-key-0002");
        secondSource = reservationOf(SECOND_CUSTOMER, "plant-key-0003");
        credits.plant(credit(OWNER_CREDIT, ownerSource), customer);
        credits.plant(credit(SECOND_CREDIT, secondSource), SECOND_CUSTOMER);
    }

    private String reservationOf(UUID holder, String key) throws Exception {
        var body = createBody(); body.put("customerId", holder.toString());
        return CatalogHttpTest.json(exchange(create(body, key, ADMIN), 201)).get("reservationId").asText();
    }

    private CreditBalance credit(UUID id, String source) {
        return new CreditBalance(id, tenant, UUID.fromString(source), CreditOrigin.CUSTOMER_CANCELLATION, new Cents(100_000), new UUID(1, 0x5C0), null, NOW.minusSeconds(3_600),
                UUID.fromString(TestJwtKeys.ACCOUNT), new Cents(0), new Cents(0));
    }

    private MockHttpServletRequestBuilder as(UUID account, UUID tenantId, MockHttpServletRequestBuilder request) throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("sub", account.toString()); claims.put("tenantId", tenantId.toString()); claims.put("roles", List.of(FINAL_CUSTOMER));
        return request.header("Authorization", "Bearer " + TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED)).header("X-Correlation-Id", TRACE);
    }

    private MockHttpServletRequestBuilder apply(UUID account, UUID tenantId, UUID credit, String target, String key) throws Exception {
        return as(account, tenantId, post(BASE + "/credit-balances/" + credit + "/applications")).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("targetReservationId", target, "amountCents", 50_000L)));
    }

    private MockHttpServletRequestBuilder refund(UUID account, UUID tenantId, UUID credit, String key) throws Exception {
        return as(account, tenantId, post(BASE + "/credit-balances/" + credit + "/refund-requests")).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("amountCents", 50_000L)));
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("data").forEach(item -> ids.add(item.get("creditBalanceId").asText()));
        return ids;
    }

    @Test void theListOfAFinalCustomerShowsTheirOwnCreditAndNeverTheCreditOfAnotherCustomer() throws Exception {
        var mine = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, tenant, get(BASE + "/credit-balances")), 200));
        var theirs = CatalogHttpTest.json(exchange(as(SECOND_ACCOUNT, tenant, get(BASE + "/credit-balances")), 200));

        assertThat(ids(mine)).containsExactly(OWNER_CREDIT.toString());
        assertThat(ids(theirs)).containsExactly(SECOND_CREDIT.toString());
        assertThat(theirs.toString()).doesNotContain(OWNER_CREDIT.toString(), customer.toString());
        var all = CatalogHttpTest.json(exchange(withToken(get(BASE + "/credit-balances"), ADMIN), 200));
        assertThat(ids(all)).containsExactlyInAnyOrder(OWNER_CREDIT.toString(), SECOND_CREDIT.toString());
    }

    @Test void theCustomerIdThatAFinalCustomerSendsDoesNotOpenTheCreditOfAnotherCustomer() throws Exception {
        var page = CatalogHttpTest.json(exchange(as(SECOND_ACCOUNT, tenant, get(BASE + "/credit-balances").param("customerId", customer.toString())), 200));

        assertThat(ids(page)).containsExactly(SECOND_CREDIT.toString());
    }

    @Test void anAccountOfAnotherTenantAndAnUnlinkedAccountSeeNoCredit() throws Exception {
        // The first account is linked in the other tenant to a customer that holds no credit there.
        assertThat(ids(CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, otherTenant, get(BASE + "/credit-balances")), 200)))).isEmpty();
        assertThat(ids(CatalogHttpTest.json(exchange(as(UNLINKED_ACCOUNT, tenant, get(BASE + "/credit-balances")), 200)))).isEmpty();
    }

    @Test void theDetailOfTheCreditOfAnotherCustomerOrTenantLooksMissingAndTheOwnerReadsTheirs() throws Exception {
        exchange(as(OWNER_ACCOUNT, tenant, get(BASE + "/credit-balances/" + OWNER_CREDIT)), 200);

        var foreign = CatalogHttpTest.json(exchange(as(SECOND_ACCOUNT, tenant, get(BASE + "/credit-balances/" + OWNER_CREDIT)), 404));
        var otherTenantRead = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, otherTenant, get(BASE + "/credit-balances/" + OWNER_CREDIT)), 404));
        var missing = CatalogHttpTest.json(exchange(as(SECOND_ACCOUNT, tenant, get(BASE + "/credit-balances/" + new UUID(1, 0x5FF))), 404));

        assertThat(foreign.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(foreign.get("error")).isEqualTo(missing.get("error"));
        assertThat(otherTenantRead.get("error")).isEqualTo(missing.get("error"));
        assertThat(foreign.toString()).doesNotContain(customer.toString(), ownerSource);
    }

    @Test void aFinalCustomerCannotApplyTheCreditOfAnotherCustomerAndNothingIsApplied() throws Exception {
        // The target is a reservation of the owner of the credit, so only the ownership of the credit stands between the caller and the value.
        Cents pending = store.reservations.get(UUID.fromString(ownerTarget)).pendingBalance();

        exchange(apply(SECOND_ACCOUNT, tenant, OWNER_CREDIT, ownerTarget, "apply-key-0001"), 403);

        assertThat(credits.applications).isEmpty();
        assertThat(credits.credits.get(OWNER_CREDIT).applied()).isEqualTo(new Cents(0));
        assertThat(store.reservations.get(UUID.fromString(ownerTarget)).pendingBalance()).isEqualTo(pending);
    }

    @Test void aFinalCustomerCannotAskTheRefundOfTheCreditOfAnotherCustomerAndNoRequestIsOpened() throws Exception {
        exchange(refund(SECOND_ACCOUNT, tenant, OWNER_CREDIT, "refund-key-0001"), 403);

        assertThat(decisions.decisions).isEmpty();
    }

    @Test void aFinalCustomerOfAnotherTenantOrWithoutACustomerRecordFindsNoCreditToApplyOrToRefund() throws Exception {
        exchange(apply(OWNER_ACCOUNT, otherTenant, OWNER_CREDIT, ownerTarget, "apply-key-0002"), 404);
        exchange(refund(OWNER_ACCOUNT, otherTenant, OWNER_CREDIT, "refund-key-0002"), 404);
        exchange(apply(UNLINKED_ACCOUNT, tenant, OWNER_CREDIT, ownerTarget, "apply-key-0003"), 404);
        exchange(refund(UNLINKED_ACCOUNT, tenant, OWNER_CREDIT, "refund-key-0003"), 404);

        assertThat(credits.applications).isEmpty();
        assertThat(decisions.decisions).isEmpty();
    }

    @Test void theOwnerAppliesTheirCreditToTheirReservationAndAsksItsRefund() throws Exception {
        var applied = CatalogHttpTest.json(exchange(apply(OWNER_ACCOUNT, tenant, OWNER_CREDIT, ownerTarget, "apply-key-0004"), 201));
        assertThat(applied.get("creditBalanceId").asText()).isEqualTo(OWNER_CREDIT.toString());
        assertThat(credits.applications).hasSize(1);
        assertThat(credits.credits.get(OWNER_CREDIT).applied()).isEqualTo(new Cents(50_000));

        var asked = CatalogHttpTest.json(exchange(refund(OWNER_ACCOUNT, tenant, OWNER_CREDIT, "refund-key-0004"), 201));
        assertThat(asked.get("creditBalanceId").asText()).isEqualTo(OWNER_CREDIT.toString());
        assertThat(decisions.decisions).hasSize(1);
    }
}
