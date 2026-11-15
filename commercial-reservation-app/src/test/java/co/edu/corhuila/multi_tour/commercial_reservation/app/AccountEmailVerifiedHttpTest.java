package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-005 AC-05.7 (RN-05) over the route receiveEvent of the contract: the event AccountEmailVerified links the account to the
// record of the tenant with the same normalized e-mail. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, CustomerHttpFixture.class})
class AccountEmailVerifiedHttpTest {
    static final UUID TENANT = UUID.fromString("0b6f2c3d-5e41-4a97-8d20-1f7a9c3e6b84");
    static final String ROUTE = "/api/v1/commercial-reservation/internal/tenants/" + TENANT + "/events";
    static final String EMAIL = "verificada@example.invalid";
    @Autowired MockMvc mvc;
    @Autowired TenantEventHttpFixture.MemoryTenants events;
    @Autowired CustomerHttpFixture.MemoryCustomers customers;

    @BeforeEach void clear() { events.clear(); customers.clear(); }

    private Customer record(UUID tenant, String email) {
        return Customer.registerLead(UUID.randomUUID(), tenant, "CC-" + UUID.randomUUID(), "Persona Ficticia",
                BirthDateOrAge.ofAge(30), new ContactInfo(email, "000-555-0100"), true, Instant.parse("2026-10-28T12:00:00Z"));
    }

    private static Map<String, Object> verified(UUID id, UUID account, String email) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("accountId", account.toString());
        payload.put("email", email);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", id.toString()); body.put("eventType", "AccountEmailVerified"); body.put("eventVersion", 1);
        body.put("tenantId", TENANT.toString()); body.put("aggregateId", account.toString());
        body.put("payload", payload); body.put("occurredAt", "2026-11-02T10:00:00Z");
        return body;
    }

    private String token(String role) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of(role));
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private MvcResult deliver(Object body, UUID key, String role, int expected) throws Exception {
        MockHttpServletRequestBuilder request = post(ROUTE).header("X-Correlation-Id", "account-email-verified-test")
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsString(body));
        if (key != null) request.header("Idempotency-Key", key.toString());
        if (role != null) request.header("Authorization", "Bearer " + token(role));
        return mvc.perform(request).andExpect(status().is(expected)).andReturn();
    }

    @Test void theVerifiedEmailLinksTheAccountToTheRecordWithThatEmailAndARepeatChangesNothing() throws Exception {
        Customer waiting = record(TENANT, EMAIL);
        Customer other = record(TENANT, "otra@example.invalid");
        customers.save(waiting);
        customers.save(other);
        UUID account = UUID.randomUUID(), id = UUID.randomUUID();

        deliver(verified(id, account, "  Verificada@Example.INVALID "), id, "SERVICE", 201);

        assertThat(customers.customers.get(waiting.customerId()).accountId()).isEqualTo(account);
        assertThat(customers.customers.get(other.customerId()).accountId()).isNull();
        assertThat(customers.customers).hasSize(2);
        assertThat(events.processed).containsExactly(id);
        deliver(verified(id, account, EMAIL), id, "SERVICE", 200);
        assertThat(customers.customers).hasSize(2);
    }

    @Test void anEventWithNoFreeRecordOfThatEmailCreatesNothingAndIsStillRecorded() throws Exception {
        Customer ofAnotherTenant = record(UUID.randomUUID(), EMAIL);
        customers.save(ofAnotherTenant);
        UUID id = UUID.randomUUID();

        deliver(verified(id, UUID.randomUUID(), EMAIL), id, "SERVICE", 201);

        assertThat(customers.customers).hasSize(1);
        assertThat(customers.customers.get(ofAnotherTenant.customerId()).accountId()).isNull();
        assertThat(events.processed).containsExactly(id);
    }

    @Test void aRecordWithAnAccountIsNeverOverwrittenAndAnAccountWithARecordLinksNoOther() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Customer linked = record(TENANT, EMAIL).linkedTo(first);
        customers.save(linked);
        UUID id = UUID.randomUUID();

        deliver(verified(id, second, EMAIL), id, "SERVICE", 201);

        assertThat(customers.customers.get(linked.customerId()).accountId()).isEqualTo(first);
        Customer free = record(TENANT, "libre@example.invalid");
        customers.save(free);
        UUID again = UUID.randomUUID();
        deliver(verified(again, first, "libre@example.invalid"), again, "SERVICE", 201);
        assertThat(customers.customers.get(free.customerId()).accountId()).isNull();
    }

    @Test void theEventIsRefusedWhenItIsIncompleteInconsistentOrNotFromTheServiceAndNothingChanges() throws Exception {
        Customer waiting = record(TENANT, EMAIL);
        customers.save(waiting);
        UUID account = UUID.randomUUID(), id = UUID.randomUUID();
        var good = verified(id, account, EMAIL);

        deliver(good, id, null, 401);
        deliver(good, id, "TENANT_ADMINISTRATOR", 403);
        deliver(good, null, "SERVICE", 400);
        deliver(good, UUID.randomUUID(), "SERVICE", 400);
        var notTheAccount = verified(id, account, EMAIL);
        notTheAccount.put("aggregateId", UUID.randomUUID().toString());
        deliver(notTheAccount, id, "SERVICE", 400);
        var otherTenant = verified(id, account, EMAIL);
        ((Map<String, Object>) otherTenant.get("payload")).put("tenantId", UUID.randomUUID().toString());
        deliver(otherTenant, id, "SERVICE", 400);
        var withoutAccount = verified(id, account, EMAIL);
        ((Map<String, Object>) withoutAccount.get("payload")).remove("accountId");
        deliver(withoutAccount, id, "SERVICE", 400);
        var withoutEmail = verified(id, account, EMAIL);
        ((Map<String, Object>) withoutEmail.get("payload")).put("email", " ");
        deliver(withoutEmail, id, "SERVICE", 400);
        var badAccount = verified(id, account, EMAIL);
        ((Map<String, Object>) badAccount.get("payload")).put("accountId", "not-a-uuid");
        deliver(badAccount, id, "SERVICE", 400);

        assertThat(customers.customers.get(waiting.customerId()).accountId()).isNull();
        assertThat(events.processed).isEmpty();
    }
}
