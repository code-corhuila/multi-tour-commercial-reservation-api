package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

// GET /discount-rules (contract listDiscountRules): paginated {data, meta}, tenant scoped, Administrator only (AC-03.5).
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, DiscountRuleHttpFixture.class})
class DiscountRuleListHttpTest {
    static final String BASE = DiscountRuleHttpTest.BASE;
    static final String ADMIN = DiscountRuleHttpTest.ADMIN;
    @Autowired MockMvc mvc;
    @Autowired DiscountRuleHttpFixture.MemoryDiscountRules rules;
    @Autowired CatalogHttpFixture.MemoryKeys keys;

    @BeforeEach void clear() { rules.rules.clear(); rules.events.clear(); keys.keys.clear(); }

    private void seed(int count, String tenant) throws Exception {
        for (int i = 0; i < count; i++) {
            var request = post(BASE).header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, ADMIN))
                    .header("Idempotency-Key", "list-key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                    .content(TestJwtKeys.JSON.writeValueAsBytes(DiscountRuleHttpTest.body()));
            status().isCreated().match(mvc.perform(request).andReturn());
        }
    }

    private MvcResult list(String query, String tenant, String role, int code) throws Exception {
        var result = mvc.perform(get(BASE + query).header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, role))
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    @Test void theAdministratorGetsTheFirstPageWithDefaultsAndNoTenantId() throws Exception {
        seed(3, TestJwtKeys.TENANT);
        var page = CatalogHttpTest.json(list("", TestJwtKeys.TENANT, ADMIN, 200));
        assertThat(page.get("data")).hasSize(3);
        assertThat(page.get("data").get(0).has("tenantId")).isFalse();
        assertThat(page.at("/meta/page").asInt()).isEqualTo(1);
        assertThat(page.at("/meta/limit").asInt()).isEqualTo(20);
        assertThat(page.at("/meta/total").asInt()).isEqualTo(3);
        assertThat(page.at("/meta/totalPages").asInt()).isEqualTo(1);
    }

    @Test void pagesAreCutByPageAndLimit() throws Exception {
        seed(3, TestJwtKeys.TENANT);
        var second = CatalogHttpTest.json(list("?page=2&limit=2", TestJwtKeys.TENANT, ADMIN, 200));
        assertThat(second.get("data")).hasSize(1);
        assertThat(second.at("/meta/total").asInt()).isEqualTo(3);
        assertThat(second.at("/meta/totalPages").asInt()).isEqualTo(2);
        assertThat(CatalogHttpTest.json(list("?page=3&limit=2", TestJwtKeys.TENANT, ADMIN, 200)).get("data")).isEmpty();
    }

    @Test void anotherTenantSeesNoneOfTheRules() throws Exception {
        seed(2, TestJwtKeys.TENANT);
        var page = CatalogHttpTest.json(list("", UUID.randomUUID().toString(), ADMIN, 200));
        assertThat(page.get("data")).isEmpty();
        assertThat(page.at("/meta/total").asInt()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"?page=0,page", "?page=abc,page", "?limit=0,limit", "?limit=101,limit", "?tenantId=x,tenantId"})
    void badPagingOrUnknownParametersAreRefusedNamingTheField(String query, String field) throws Exception {
        CatalogHttpTest.assertField(list(query, TestJwtKeys.TENANT, ADMIN, 400), field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATIONAL_COLLABORATOR", "MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER"})
    void onlyTheTenantAdministratorLists(String role) throws Exception {
        CatalogHttpTest.assertForbidden(list("", TestJwtKeys.TENANT, role, 403));
    }

    @Test void withoutTokenTheAnswerIs401() throws Exception {
        status().isUnauthorized().match(mvc.perform(get(BASE)).andReturn());
    }
}
