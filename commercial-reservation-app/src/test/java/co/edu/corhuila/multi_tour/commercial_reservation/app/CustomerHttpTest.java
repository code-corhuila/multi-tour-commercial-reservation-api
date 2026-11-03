package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-005 over HTTP (api spec T-04, T-06, T-07): the three routes of the Customers tag. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, CustomerHttpFixture.class})
class CustomerHttpTest {
    static final String BASE = "/api/v1/commercial-reservation/customers";
    static final String TRACE = CatalogHttpTest.TRACE;
    static final String ADMIN = "TENANT_ADMINISTRATOR", COLLABORATOR = "OPERATIONAL_COLLABORATOR";
    static final String OTHER_TENANT = "00000000-0000-0000-0000-000000000999";
    static final String PERMISSION = CustomerAuthorization.SENSITIVE_DATA_PERMISSION;
    static final String BLOOD = "ZZ-NEG-FICTICIA", EMERGENCY = "Contacto Ficticio 000-555-0199",
            RESTRICTION = "Restriccion ficticia";
    final UUID tenant = UUID.fromString(TestJwtKeys.TENANT);
    final UUID plainService = UUID.randomUUID(), riskyService = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired CustomerHttpFixture.MemoryCustomers store;
    @Autowired CatalogHttpFixture.MemoryKeys keys;

    @BeforeEach void clear() {
        store.clear(); keys.keys.clear();
        store.services.put(plainService, new CustomerHttpFixture.MemoryCustomers.Service(tenant, false));
        store.services.put(riskyService, new CustomerHttpFixture.MemoryCustomers.Service(tenant, true));
    }

    Map<String, Object> body(UUID service) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Persona Ficticia Uno"); body.put("documentNumber", " ab-0000 ");
        body.put("birthDate", "1990-05-17");
        body.put("contact", Map.of("email", "  Persona.UNO@Example.invalid ", "phone", "3000000000"));
        body.put("termsAccepted", true); body.put("serviceId", service.toString());
        return body;
    }

    Map<String, Object> riskyBody() {
        var body = body(riskyService);
        body.put("informedConsentSigned", true);
        body.put("emergencyInfo", Map.of("bloodType", BLOOD, "emergencyContact", EMERGENCY,
                "mobilityRestrictions", RESTRICTION));
        return body;
    }

    @Test void adminRegistersTheLeadWithItsLocationOneEventAndNoSensitiveData() throws Exception {
        var result = exchange(create(riskyBody(), "customer-key-1", TestJwtKeys.TENANT, ADMIN), 201);
        var customer = CatalogHttpTest.json(result);
        assertThat(customer.get("fullName").asText()).isEqualTo("Persona Ficticia Uno");
        assertThat(customer.get("documentNumber").asText()).isEqualTo("AB-0000");
        assertThat(customer.at("/contact/email").asText()).isEqualTo("persona.uno@example.invalid");
        assertThat(customer.get("birthDate").asText()).isEqualTo("1990-05-17");
        assertThat(customer.get("termsAccepted").asBoolean()).isTrue();
        assertThat(customer.get("createdAt").asText()).endsWith("Z");
        assertThat(customer.get("highRisk").asBoolean()).isTrue();
        assertThat(customer.has("tenantId")).isFalse();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(BLOOD, EMERGENCY, RESTRICTION)
                .doesNotContain("emergencyInfo", "informedConsentSigned");
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo(BASE + "/" + customer.get("customerId").asText());
        assertThat(store.registered).hasSize(1);
    }

    @Test void theCollaboratorRegistersAnOrdinaryLeadWithAnAgeInsteadOfABirthDate() throws Exception {
        var request = body(plainService); request.remove("birthDate"); request.put("age", 34);
        var customer = CatalogHttpTest.json(exchange(create(request, "customer-key-2", TestJwtKeys.TENANT, COLLABORATOR), 201));
        assertThat(customer.get("age").asInt()).isEqualTo(34);
        assertThat(customer.has("birthDate")).isFalse();
        assertThat(customer.get("highRisk").asBoolean()).isFalse();
    }

    @Test void repeatingTheSameKeyAnswers200WithTheSameCustomerAndNoSecondOne() throws Exception {
        var first = CatalogHttpTest.json(exchange(create(body(plainService), "customer-key-3", TestJwtKeys.TENANT, ADMIN), 201));
        var second = CatalogHttpTest.json(exchange(create(body(plainService), "customer-key-3", TestJwtKeys.TENANT, ADMIN), 200));
        // The replay is a read: it answers the same customer without the registration-only highRisk.
        assertThat(second).isEqualTo(((com.fasterxml.jackson.databind.node.ObjectNode) first.deepCopy()).without("highRisk"));
        assertThat(store.customers).hasSize(1);
        assertThat(store.registered).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"", "short"})
    void aMissingOrShortKeyIsRefusedNamingTheHeader(String key) throws Exception {
        CatalogHttpTest.assertField(exchange(create(body(plainService), key.isEmpty() ? null : key,
                TestJwtKeys.TENANT, ADMIN), 400), "Idempotency-Key");
        assertThat(store.customers).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"fullName", "documentNumber", "contact", "termsAccepted", "serviceId"})
    void aMissingRequiredFieldIsRefusedNamingIt(String field) throws Exception {
        var request = body(plainService); request.remove(field);
        CatalogHttpTest.assertField(exchange(create(request, "customer-key-4", TestJwtKeys.TENANT, ADMIN), 400), field);
        assertThat(store.customers).isEmpty();
        assertThat(store.registered).isEmpty();
    }

    @Test void termsNotAcceptedAFutureBirthDateAndATenantInTheBodyAreRefused() throws Exception {
        var declined = body(plainService); declined.put("termsAccepted", false);
        CatalogHttpTest.assertField(exchange(create(declined, "customer-key-5", TestJwtKeys.TENANT, ADMIN), 400), "termsAccepted");
        var future = body(plainService); future.put("birthDate", "2027-01-01");
        CatalogHttpTest.assertField(exchange(create(future, "customer-key-6", TestJwtKeys.TENANT, ADMIN), 400), "birthDate");
        var tenantInBody = body(plainService); tenantInBody.put("tenantId", UUID.randomUUID().toString());
        CatalogHttpTest.assertField(exchange(create(tenantInBody, "customer-key-7", TestJwtKeys.TENANT, ADMIN), 400), "tenantId");
        assertThat(store.customers).isEmpty();
    }

    @Test void aHighRiskActivityWithoutTheExtraDataNamesEachMissingField() throws Exception {
        var result = exchange(create(body(riskyService), "customer-key-8", TestJwtKeys.TENANT, ADMIN), 400);
        var fields = new java.util.ArrayList<String>();
        CatalogHttpTest.json(result).get("details").forEach(detail -> fields.add(detail.get("field").asText()));
        assertThat(fields).containsExactlyInAnyOrder("bloodType", "emergencyContact", "mobilityRestrictions",
                "informedConsentSigned");
        assertThat(store.customers).isEmpty();
    }

    @Test void aServiceOfAnotherTenantOrUnknownIsRefusedNamingServiceId() throws Exception {
        store.services.put(UUID.randomUUID(), new CustomerHttpFixture.MemoryCustomers.Service(UUID.randomUUID(), true));
        CatalogHttpTest.assertField(exchange(create(body(UUID.randomUUID()), "customer-key-9", TestJwtKeys.TENANT, ADMIN), 400),
                "serviceId");
        assertThat(store.customers).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER"})
    void onlyAdministratorAndCollaboratorRegister(String role) throws Exception {
        // An invalid body must not leak validation details to a caller without permission.
        CatalogHttpTest.assertForbidden(exchange(create(Map.of(), "customer-key-10", TestJwtKeys.TENANT, role), 403));
        assertThat(store.customers).isEmpty();
    }

    @Test void aRequestWithoutTokenIsUnauthorizedAndATokenWithoutTenantIsForbidden() throws Exception {
        CatalogHttpTest.assertError(exchange(get(BASE + "/" + UUID.randomUUID()), 401), "UNAUTHORIZED",
                "authentication is required");
        CatalogHttpTest.assertForbidden(exchange(read(UUID.randomUUID().toString(), null, ADMIN), 403));
    }

    @ParameterizedTest @ValueSource(strings = {"TENANT_ADMINISTRATOR", "OPERATIONAL_COLLABORATOR", "MANAGER", "ANALYST"})
    void fourRolesReadTheOrdinaryDataWithoutTheRiskDataAndWithoutAskingForIt(String role) throws Exception {
        var id = seed();
        var customer = CatalogHttpTest.json(exchange(read(id, TestJwtKeys.TENANT, role), 200));
        assertThat(customer.get("customerId").asText()).isEqualTo(id);
        assertThat(customer.get("documentNumber").asText()).isEqualTo("AB-0000");
        assertThat(customer.toString()).doesNotContain(BLOOD, EMERGENCY, RESTRICTION).doesNotContain("emergencyInfo");
        assertThat(store.fullReads).isZero();
        assertThat(store.accessed).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"ACCOUNTANT", "FINAL_CUSTOMER"})
    void theAccountantAndTheFinalCustomerCannotReadEvenWithTheClaim(String role) throws Exception {
        var id = seed();
        CatalogHttpTest.assertForbidden(exchange(read(id, TestJwtKeys.TENANT, role, List.of(PERMISSION)), 403));
        assertThat(store.profileReads).isZero();
    }

    @Test void aCustomerOfAnotherTenantAndAnUnknownOneAnswerTheSameNotFound() throws Exception {
        var id = seed();
        CatalogHttpTest.assertError(exchange(read(id, OTHER_TENANT, ADMIN), 404), "NOT_FOUND", "resource not found");
        CatalogHttpTest.assertError(exchange(read(UUID.randomUUID().toString(), TestJwtKeys.TENANT, ADMIN), 404),
                "NOT_FOUND", "resource not found");
        CatalogHttpTest.assertError(exchange(sensitive(id, OTHER_TENANT, ADMIN, List.of(PERMISSION), null), 404),
                "NOT_FOUND", "resource not found");
    }

    @Test void aNonUuidIdentifierIsAValidationError() throws Exception {
        CatalogHttpTest.assertField(exchange(read("not-a-uuid", TestJwtKeys.TENANT, ADMIN), 400), "customerId");
    }

    @ParameterizedTest @ValueSource(strings = {"TENANT_ADMINISTRATOR", "OPERATIONAL_COLLABORATOR"})
    void theHolderOfThePermissionReadsTheSensitiveDataAuditedAndNotCacheable(String role) throws Exception {
        var id = seed();
        var result = exchange(sensitive(id, TestJwtKeys.TENANT, role, List.of(PERMISSION), "motivo ficticio"), 200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        var data = CatalogHttpTest.json(result);
        assertThat(data.get("customerId").asText()).isEqualTo(id);
        assertThat(data.at("/emergencyInfo/bloodType").asText()).isEqualTo(BLOOD);
        assertThat(data.at("/emergencyInfo/emergencyContact").asText()).isEqualTo(EMERGENCY);
        assertThat(data.at("/emergencyInfo/mobilityRestrictions").asText()).isEqualTo(RESTRICTION);
        assertThat(data.get("informedConsentSigned").asBoolean()).isTrue();
        assertThat(store.accessed).hasSize(1);
        assertThat(store.accessed.get(0).reason()).isEqualTo("motivo ficticio");
        assertThat(store.accessed.get(0).customerId()).hasToString(id);
    }

    @Test void aReasonIsOptionalAndATooLongReasonIsRefused() throws Exception {
        var id = seed();
        exchange(sensitive(id, TestJwtKeys.TENANT, ADMIN, List.of(PERMISSION), null), 200);
        assertThat(store.accessed.get(0).reason()).isNull();
        CatalogHttpTest.assertField(exchange(sensitive(id, TestJwtKeys.TENANT, ADMIN, List.of(PERMISSION),
                "x".repeat(501)), 400), "reason");
        assertThat(store.accessed).hasSize(1);
    }

    @Test void withoutThePermissionOrWithAnotherValueTheSensitiveDataIsDeniedAndNothingIsAudited() throws Exception {
        var id = seed();
        for (var permissions : List.<List<String>>of(List.of(), List.of("SOMETHING_ELSE"))) {
            var result = exchange(sensitive(id, TestJwtKeys.TENANT, ADMIN, permissions, null), 403);
            CatalogHttpTest.assertForbidden(result);
            assertThat(result.getResponse().getContentAsString()).doesNotContain(BLOOD, EMERGENCY, RESTRICTION);
        }
        assertThat(store.accessed).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER"})
    void aForgedClaimOnAReadOnlyRoleIsDenied(String role) throws Exception {
        var id = seed();
        CatalogHttpTest.assertForbidden(exchange(sensitive(id, TestJwtKeys.TENANT, role, List.of(PERMISSION), null), 403));
        assertThat(store.accessed).isEmpty();
    }

    @Test void ifTheAuditFailsNothingIsReturned() throws Exception {
        var id = seed();
        store.failOnAudit = new IllegalStateException("audit down");
        var result = exchange(sensitive(id, TestJwtKeys.TENANT, ADMIN, List.of(PERMISSION), null), 500);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(BLOOD, EMERGENCY, RESTRICTION);
        assertThat(store.accessed).isEmpty();
    }

    @Test void aCustomerWithoutRiskDataHasNothingToReadButTheConsultationIsStillAudited() throws Exception {
        var id = seedOrdinary();
        CatalogHttpTest.assertError(exchange(sensitive(id, TestJwtKeys.TENANT, ADMIN, List.of(PERMISSION), null), 404),
                "NOT_FOUND", "resource not found");
        assertThat(store.accessed).hasSize(1);
    }

    @Test void aPermissionsClaimThatIsNotAnArrayOfStringsIsAnInvalidToken() throws Exception {
        var claims = TestJwtKeys.claims(); claims.put("permissions", "CONSULT_SENSITIVE_DATA");
        var token = TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
        CatalogHttpTest.assertError(exchange(get(BASE + "/" + seed()).header("Authorization", "Bearer " + token), 401),
                "UNAUTHORIZED", "authentication is required");
    }

    private String seed() {
        var data = new HighRiskData(BLOOD, EMERGENCY, RESTRICTION, true);
        var customer = Customer.registerLead(UUID.randomUUID(), tenant, " ab-0000 ", "Persona Ficticia Uno",
                BirthDateOrAge.ofAge(34), new ContactInfo("persona.uno@example.invalid", "3000000000"), true,
                TestJwtKeys.NOW, true, data);
        store.save(customer);
        return customer.customerId().toString();
    }

    private String seedOrdinary() {
        var customer = Customer.registerLead(UUID.randomUUID(), tenant, "AB-0001", "Persona Ficticia Dos",
                BirthDateOrAge.ofAge(41), new ContactInfo("persona.dos@example.invalid", "3000000001"), true,
                TestJwtKeys.NOW);
        store.save(customer);
        return customer.customerId().toString();
    }

    private MockHttpServletRequestBuilder create(Map<String, Object> request, String key, String tenantId, String role)
            throws Exception {
        var builder = post(BASE).header("Authorization", "Bearer " + token(tenantId, role, List.of()))
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(request));
        if (key != null) builder.header("Idempotency-Key", key);
        return builder;
    }

    private MockHttpServletRequestBuilder read(String id, String tenantId, String role) throws Exception {
        return read(id, tenantId, role, List.of());
    }

    private MockHttpServletRequestBuilder read(String id, String tenantId, String role, List<String> permissions)
            throws Exception {
        return get(BASE + "/" + id).header("Authorization", "Bearer " + token(tenantId, role, permissions));
    }

    private MockHttpServletRequestBuilder sensitive(String id, String tenantId, String role, List<String> permissions,
            String reason) throws Exception {
        var builder = get(BASE + "/" + id + "/sensitive-data")
                .header("Authorization", "Bearer " + token(tenantId, role, permissions));
        if (reason != null) builder.param("reason", reason);
        return builder;
    }

    static String token(String tenantId, String role, List<String> permissions) throws Exception {
        var claims = TestJwtKeys.claims();
        if (tenantId == null) claims.remove("tenantId"); else claims.put("tenantId", tenantId);
        claims.put("roles", List.of(role)); claims.put("permissions", permissions);
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private MvcResult exchange(MockHttpServletRequestBuilder request, int code) throws Exception {
        MvcResult result = mvc.perform(request.header("X-Correlation-Id", TRACE)).andReturn();
        status().is(code).match(result);
        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isEqualTo(TRACE);
        return result;
    }
}
