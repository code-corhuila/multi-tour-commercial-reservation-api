package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// GET /discount-quotes (contract quoteCurrentDiscounts, HU-COM-004): AC-04.1, AC-04.2, AC-04.10 and the roles of the row.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, DiscountRuleHttpFixture.class})
class DiscountQuoteHttpTest {
    static final String BASE = "/api/v1/commercial-reservation/discount-quotes";
    static final String ADMIN = DiscountRuleHttpTest.ADMIN;
    @Autowired MockMvc mvc;
    @Autowired DiscountRuleHttpFixture.MemoryDiscountRules rules;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;
    @Autowired CatalogHttpFixture.MemoryKeys keys;
    private UUID service;

    @BeforeEach void seedATourAndAFifteenPercentRule() throws Exception {
        rules.rules.clear(); rules.events.clear(); keys.keys.clear(); catalog.items.clear();
        service = UUID.randomUUID();
        catalog.save(CatalogService.register(service, UUID.fromString(TestJwtKeys.TENANT), CatalogServiceType.TOUR,
                "Tour", CommercialTerms.of(35_000_000L, 21_000_000L, 18, true, false), TestJwtKeys.NOW));
        var create = post(DiscountRuleHttpTest.BASE).header("Authorization", "Bearer " + CatalogHttpTest.token(TestJwtKeys.TENANT, ADMIN))
                .header("Idempotency-Key", "quote-seed-1").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(DiscountRuleHttpTest.body()));
        status().isCreated().match(mvc.perform(create).andReturn());
    }

    private MvcResult quote(String query, String tenant, String role, int code) throws Exception {
        var result = mvc.perform(get(BASE + query).header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, role))
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    private String query(Object serviceId, String date, String base) {
        return "?serviceId=" + serviceId + "&referenceDate=" + date + "&baseCents=" + base;
    }

    @Test void aRuleInForceGivesTheBreakdownOfTheContract() throws Exception {
        var body = CatalogHttpTest.json(quote(query(service, "2026-11-15", "100000"), TestJwtKeys.TENANT, ADMIN, 200));
        assertThat(body.get("baseCents").asLong()).isEqualTo(100_000);
        assertThat(body.get("finalValueCents").asLong()).isEqualTo(85_000);
        var line = body.get("lines").get(0);
        assertThat(line.get("order").asInt()).isEqualTo(1);
        assertThat(line.get("kind").asText()).isEqualTo("RULE");
        assertThat(line.get("name").asText()).isEqualTo("Early booking");
        assertThat(line.get("calculationBase").asText()).isEqualTo("ORIGINAL_VALUE");
        assertThat(line.get("baseCents").asLong()).isEqualTo(100_000);
        assertThat(line.get("amountCents").asLong()).isEqualTo(15_000);
        assertThat(line.get("subtotalCents").asLong()).isEqualTo(85_000);
    }

    @Test void outsideTheValidityOnlyTheBaseValueComesBackWithNoLines() throws Exception {
        var body = CatalogHttpTest.json(quote(query(service, "2026-12-15", "100000"), TestJwtKeys.TENANT, ADMIN, 200));
        assertThat(body.get("lines")).isEmpty();
        assertThat(body.get("finalValueCents").asLong()).isEqualTo(body.get("baseCents").asLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TENANT_ADMINISTRATOR", "MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER"})
    void theRolesOfTheQuoteRowCanQuote(String role) throws Exception {
        quote(query(service, "2026-11-15", "100000"), TestJwtKeys.TENANT, role, 200);
    }

    @Test void theOperationalCollaboratorIsRefusedBeforeAnyValidation() throws Exception {
        CatalogHttpTest.assertForbidden(quote("", TestJwtKeys.TENANT, "OPERATIONAL_COLLABORATOR", 403));
    }

    @Test void aTourOfAnotherTenantOrAnUnknownOneAnswersTheSame404() throws Exception {
        var other = CatalogHttpTest.json(quote(query(service, "2026-11-15", "1"), UUID.randomUUID().toString(), ADMIN, 404));
        var unknown = CatalogHttpTest.json(quote(query(UUID.randomUUID(), "2026-11-15", "1"), TestJwtKeys.TENANT, ADMIN, 404));
        assertThat(other.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(other.get("message")).isEqualTo(unknown.get("message"));
    }

    @ParameterizedTest
    @CsvSource({"serviceId", "referenceDate", "baseCents"})
    void aMissingParameterIsRefusedNamingIt(String missing) throws Exception {
        var q = new StringBuilder("?");
        if (!missing.equals("serviceId")) q.append("serviceId=").append(service).append('&');
        if (!missing.equals("referenceDate")) q.append("referenceDate=2026-11-15&");
        if (!missing.equals("baseCents")) q.append("baseCents=100");
        CatalogHttpTest.assertField(quote(q.toString(), TestJwtKeys.TENANT, ADMIN, 400), missing);
    }

    @ParameterizedTest
    @CsvSource({"not-a-uuid,2026-11-15,100,serviceId", "SERVICE,15-11-2026,100,referenceDate",
            "SERVICE,2026-02-30,100,referenceDate", "SERVICE,2026-11-15,-1,baseCents", "SERVICE,2026-11-15,1.5,baseCents"})
    void anInvalidValueIsRefusedNamingTheField(String id, String date, String base, String field) throws Exception {
        var serviceId = id.equals("SERVICE") ? service : id;
        CatalogHttpTest.assertField(quote(query(serviceId, date, base), TestJwtKeys.TENANT, ADMIN, 400), field);
    }

    @Test void anUnknownParameterIsRefused() throws Exception {
        CatalogHttpTest.assertField(quote(query(service, "2026-11-15", "100") + "&tenantId=x", TestJwtKeys.TENANT, ADMIN, 400),
                "tenantId");
    }

    @Test void withoutTokenTheAnswerIs401() throws Exception {
        status().isUnauthorized().match(mvc.perform(get(BASE + query(service, "2026-11-15", "1"))).andReturn());
    }
}
