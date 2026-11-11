package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// GET /customers?search= over HTTP (api spec 005, F-18 amended 2026-11-09): the picker of HU-COM-007. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, CustomerHttpFixture.class})
class CustomerSearchHttpTest {
    static final String BASE = CustomerHttpTest.BASE;
    static final String BLOOD = "ZZ-NEG-FICTICIA", EMERGENCY = "Contacto Ficticio 000-555-0199", RESTRICTION = "Restriccion ficticia";
    final UUID tenant = UUID.fromString(TestJwtKeys.TENANT);
    @Autowired MockMvc mvc;
    @Autowired CustomerHttpFixture.MemoryCustomers store;
    @Autowired CatalogHttpFixture.MemoryKeys keys;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach void seed() {
        store.clear(); keys.keys.clear();
        person(tenant, "Marcela Ficticia Uno", true);
        person(tenant, "Marcela Ficticia Dos", false);
        person(tenant, "Mario Ficticio", false);
        person(UUID.fromString(CustomerHttpTest.OTHER_TENANT), "Marcela De Otro Tenant", false);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    @AfterEach void release() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }

    private void person(UUID owner, String name, boolean risky) {
        var data = risky ? new HighRiskData(BLOOD, EMERGENCY, RESTRICTION, true) : null;
        store.save(Customer.registerLead(UUID.randomUUID(), owner, "DOC-" + name.hashCode(), name, BirthDateOrAge.ofAge(34),
                new ContactInfo("persona@example.invalid", "3000000000"), true, TestJwtKeys.NOW, risky, data));
    }

    private MockHttpServletRequestBuilder search(String tenantId, String role, String text) throws Exception {
        var builder = get(BASE).header("Authorization", "Bearer " + CustomerHttpTest.token(tenantId, role, List.of()));
        if (text != null) builder.param("search", text);
        return builder;
    }

    private MvcResult exchange(MockHttpServletRequestBuilder request, int code) throws Exception {
        MvcResult result = mvc.perform(request.header("X-Correlation-Id", CustomerHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    @Test void theAdministratorFindsTheCustomersOfItsTenantByTheStartOfTheNameWithOrdinaryDataOnly() throws Exception {
        var result = exchange(search(TestJwtKeys.TENANT, "TENANT_ADMINISTRATOR", " marcela f "), 200);
        var page = CatalogHttpTest.json(result);
        assertThat(page.get("data")).hasSize(2);
        assertThat(page.at("/data/0/fullName").asText()).isEqualTo("Marcela Ficticia Dos");
        assertThat(page.at("/data/1/fullName").asText()).isEqualTo("Marcela Ficticia Uno");
        assertThat(page.at("/data/0/contact/email").asText()).isEqualTo("persona@example.invalid");
        assertThat(page.get("next").isNull()).isTrue();
        assertThat(page.at("/data/0").has("tenantId")).isFalse();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(BLOOD, EMERGENCY, RESTRICTION)
                .doesNotContain("emergencyInfo", "informedConsentSigned", "De Otro Tenant");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(store.fullReads).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"TENANT_ADMINISTRATOR", "OPERATIONAL_COLLABORATOR", "MANAGER", "ANALYST"})
    void theFourReadRolesSearch(String role) throws Exception {
        assertThat(CatalogHttpTest.json(exchange(search(TestJwtKeys.TENANT, role, "mar"), 200)).get("data")).hasSize(3);
    }

    @ParameterizedTest @ValueSource(strings = {"ACCOUNTANT", "FINAL_CUSTOMER"})
    void theAccountantAndTheFinalCustomerAreRefusedAndNothingIsRead(String role) throws Exception {
        CatalogHttpTest.assertForbidden(exchange(search(TestJwtKeys.TENANT, role, "mar"), 403));
        assertThat(store.searches).isZero();
    }

    @Test void withoutATokenTheAnswerIs401() throws Exception {
        exchange(get(BASE).param("search", "mar"), 401);
    }

    @Test void theTenantIsTheOneOfTheTokenAndNeverSeesTheOthers() throws Exception {
        var other = CatalogHttpTest.json(exchange(search(CustomerHttpTest.OTHER_TENANT, "MANAGER", "marcela"), 200));
        assertThat(other.get("data")).hasSize(1);
        assertThat(other.at("/data/0/fullName").asText()).isEqualTo("Marcela De Otro Tenant");
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "m", "  m  "})
    void aShortOrMissingTextIsAValidationErrorNamingTheField(String text) throws Exception {
        CatalogHttpTest.assertField(exchange(search(TestJwtKeys.TENANT, "MANAGER", text), 400), "search");
        CatalogHttpTest.assertField(exchange(search(TestJwtKeys.TENANT, "MANAGER", null), 400), "search");
        assertThat(store.searches).isZero();
    }

    @Test void aLimitBelowOneIsRefusedAndAHugeOneIsCapped() throws Exception {
        CatalogHttpTest.assertField(exchange(search(TestJwtKeys.TENANT, "MANAGER", "mar").param("limit", "0"), 400), "limit");
        exchange(search(TestJwtKeys.TENANT, "MANAGER", "mar").param("limit", "500"), 200);
        assertThat(store.lastSearchLimit).isEqualTo(51);
    }

    @Test void theNextPageContinuesWithTheCursorAndTheLastOneHasNone() throws Exception {
        var first = CatalogHttpTest.json(exchange(search(TestJwtKeys.TENANT, "MANAGER", "mar").param("limit", "2"), 200));
        assertThat(first.get("data")).hasSize(2);
        assertThat(first.get("next").asText()).isNotBlank();
        var second = CatalogHttpTest.json(exchange(search(TestJwtKeys.TENANT, "MANAGER", "mar").param("limit", "2")
                .param("cursor", first.get("next").asText()), 200));
        assertThat(second.get("data")).hasSize(1);
        assertThat(second.at("/data/0/fullName").asText()).isEqualTo("Mario Ficticio");
        assertThat(second.get("next").isNull()).isTrue();
    }

    @Test void aCursorNotIssuedByTheServiceIsAValidationErrorNamingTheField() throws Exception {
        CatalogHttpTest.assertField(exchange(search(TestJwtKeys.TENANT, "MANAGER", "mar").param("cursor", "not-a-cursor"), 400), "cursor");
    }

    @Test void theTextSearchedAndTheNamesFoundNeverReachTheLogs() throws Exception {
        String answer = exchange(search(TestJwtKeys.TENANT, "MANAGER", "Marcela Ficticia"), 200).getResponse().getContentAsString();
        exchange(search(TestJwtKeys.TENANT, "MANAGER", "x"), 400);
        var text = new StringBuilder();
        for (ILoggingEvent event : logs.list) {
            text.append('\n').append(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) text.append('\n').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            event.getMDCPropertyMap().values().forEach(value -> text.append('\n').append(value));
        }
        assertThat(answer).contains("Marcela Ficticia Uno");
        assertThat(text.toString().toLowerCase()).doesNotContain("marcela").doesNotContain("persona@example.invalid");
    }
}
