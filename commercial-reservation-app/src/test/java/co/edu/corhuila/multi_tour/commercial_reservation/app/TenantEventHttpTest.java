package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
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

// api spec 021 T-03 (RN-TSC-01, -02, -06): operation receiveEvent of the contract. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class})
class TenantEventHttpTest {
    static final UUID TENANT = UUID.fromString("0b6f2c3d-5e41-4a97-8d20-1f7a9c3e6b84");
    static final String ROUTE = "/api/v1/commercial-reservation/internal/tenants/" + TENANT + "/events";
    static final String TRACE = "tenant-event-http-test";
    @Autowired MockMvc mvc;
    @Autowired TenantEventHttpFixture.MemoryTenants store;

    @BeforeEach void clear() { store.clear(); }

    static Map<String, Object> event(String type, UUID id, String at) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("identifier", "agencia-ficticia");
        payload.put("commercialName", "Agencia Ficticia");
        payload.put("tenantStatus", "ACTIVE");
        payload.put("timeZone", "America/Bogota");
        payload.put("country", "CO");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", id.toString()); body.put("eventType", type); body.put("eventVersion", 1);
        body.put("tenantId", TENANT.toString()); body.put("aggregateId", TENANT.toString());
        body.put("payload", payload); body.put("occurredAt", at);
        return body;
    }

    String token(String role) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of(role));
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    MockHttpServletRequestBuilder call(Object body, String key, String bearer) throws Exception {
        var request = post(ROUTE).header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String text ? text : TestJwtKeys.JSON.writeValueAsString(body));
        if (key != null) request.header("Idempotency-Key", key);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return request;
    }

    MvcResult exchange(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return mvc.perform(request).andExpect(status().is(expected)).andReturn();
    }

    @Test void receivingRouteAnswers401WithoutTokenAnd403WithoutTheServiceRole() throws Exception {
        UUID id = UUID.randomUUID();
        var body = event("TenantCreated", id, "2026-10-29T14:05:00Z");

        exchange(call(body, id.toString(), null), 401);
        exchange(call(body, id.toString(), token("TENANT_ADMINISTRATOR")), 403);
        exchange(call(body, id.toString(), token("PLATFORM_ADMINISTRATOR")), 403);
        assertThat(store.copies).isEmpty();
    }

    @Test void firstDeliveryIs201AndARepeatIs200() throws Exception {
        UUID id = UUID.randomUUID();
        var body = event("TenantCreated", id, "2026-10-29T14:05:00Z");

        exchange(call(body, id.toString(), token("SERVICE")), 201);
        exchange(call(body, id.toString(), token("SERVICE")), 200);

        var copy = store.copies.get(TENANT);
        assertThat(copy.identifier()).isEqualTo("agencia-ficticia");
        assertThat(copy.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(copy.lastEventAt()).isEqualTo(Instant.parse("2026-10-29T14:05:00Z"));
        UUID later = UUID.randomUUID();
        exchange(call(event("TenantInactivated", later, "2026-10-29T14:06:00Z"), later.toString(), token("SERVICE")), 201);
        assertThat(store.copies.get(TENANT).status()).isEqualTo(TenantStatus.INACTIVE);
        UUID older = UUID.randomUUID();
        exchange(call(event("TenantReactivated", older, "2026-10-29T14:00:00Z"), older.toString(), token("SERVICE")), 200);
        assertThat(store.copies.get(TENANT).status()).isEqualTo(TenantStatus.INACTIVE);
        assertThat(store.processed).contains(id, later, older);
    }

    @Test void receivingRouteValidatesTheBodyAsTheContractSays() throws Exception {
        UUID id = UUID.randomUUID();
        String bearer = token("SERVICE");
        var good = event("TenantCreated", id, "2026-10-29T14:05:00Z");

        exchange(call(good, null, bearer), 400);
        exchange(call(good, UUID.randomUUID().toString(), bearer), 400);
        exchange(call("{not json", id.toString(), bearer), 400);
        exchange(call(event("DepartureScheduled", id, "2026-10-29T14:05:00Z"), id.toString(), bearer), 400);
        exchange(call(event("TenantCreated", id, "yesterday"), id.toString(), bearer), 400);
        var otherTenant = event("TenantCreated", id, "2026-10-29T14:05:00Z");
        otherTenant.put("tenantId", UUID.randomUUID().toString());
        exchange(call(otherTenant, id.toString(), bearer), 400);
        var otherAggregate = event("TenantCreated", id, "2026-10-29T14:05:00Z");
        otherAggregate.put("aggregateId", UUID.randomUUID().toString());
        exchange(call(otherAggregate, id.toString(), bearer), 400);
        var pending = event("TenantCreated", id, "2026-10-29T14:05:00Z");
        ((Map<String, Object>) pending.get("payload")).put("tenantStatus", "PENDING");
        exchange(call(pending, id.toString(), bearer), 400);
        UUID inactive = UUID.randomUUID();
        var withoutCopy = event("TenantInactivated", inactive, "2026-10-29T14:05:00Z");
        ((Map<String, Object>) withoutCopy.get("payload")).remove("identifier");
        exchange(call(withoutCopy, inactive.toString(), bearer), 400);
        exchange(call(good, id.toString(), bearer).contentType(MediaType.TEXT_PLAIN), 415);
        exchange(call("{\"pad\":\"" + "x".repeat(16 * 1024) + "\"}", id.toString(), bearer), 413);
        assertThat(store.copies).isEmpty();
        assertThat(store.processed).isEmpty();

        var tolerant = event("TenantCreated", id, "2026-10-29T14:05:00Z");
        tolerant.put("unknownField", "tolerated");
        exchange(call(tolerant, id.toString(), bearer), 201);
        var error = CatalogHttpTest.json(exchange(call(good, null, bearer), 400));
        assertThat(error.get("error").asText()).isEqualTo("VALIDATION_ERROR");
    }
}
