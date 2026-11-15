package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-005 api spec T-07 (RN-11, AC-05.14, AC-05.15): the companions travel with the reservation, so a tenant that is not
// the owner neither sees them nor stores them, and gets the same answer as for a resource that does not exist.
class ReservationCompanionsTenantHttpTest extends ReservationHttpBase {
    private final java.util.UUID foreignCustomer = new java.util.UUID(1, 90);

    @BeforeEach void anotherActiveTenantWithItsOwnCustomer() {
        directory.tenants.put(otherTenant.toString(), new TenantRef(otherTenant, true));
        customers.save(Customer.registerLead(foreignCustomer, otherTenant, "CC99999", "Persona Ajena", BirthDateOrAge.ofAge(40),
                new ContactInfo("ajena@example.invalid", "3000000001"), true, NOW));
    }

    private Map<String, Object> withCompanion(Map<String, Object> body) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fullName", "Acompanante Ajeno"); item.put("documentNumber", "CC 888"); item.put("age", 25);
        body.put("companions", List.of(item));
        return body;
    }

    private MockHttpServletRequestBuilder asOtherTenant(MockHttpServletRequestBuilder request) throws Exception {
        return request.header("Authorization", "Bearer " + CatalogHttpTest.token(otherTenant.toString(), ADMIN))
                .header("X-Correlation-Id", TRACE);
    }

    @Test void aCustomerOfAnotherTenantNeverReceivesCompanionsAndNothingIsStored() throws Exception {
        var body = withCompanion(createBody());
        body.put("customerId", foreignCustomer.toString());
        var foreign = CatalogHttpTest.json(exchange(create(body, "companion-tenant-1", ADMIN), 404));
        var missing = withCompanion(createBody());
        missing.put("customerId", new java.util.UUID(1, 91).toString());
        var absent = CatalogHttpTest.json(exchange(create(missing, "companion-tenant-2", ADMIN), 404));
        assertThat(foreign.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(foreign.get("message")).isEqualTo(absent.get("message"));
        assertThat(store.reservations).isEmpty();
    }

    @Test void theReservationWithCompanionsOfOneTenantAnswersNotFoundToAnotherAndExposesNoCompanion() throws Exception {
        var created = CatalogHttpTest.json(exchange(create(withCompanion(createBody()), "companion-tenant-3", ADMIN), 201));
        String id = created.get("reservationId").asText();
        var url = BASE + "/reservations/" + id;
        var foreign = CatalogHttpTest.json(exchange(asOtherTenant(get(url)), 404));
        var absent = CatalogHttpTest.json(exchange(asOtherTenant(get(BASE + "/reservations/" + new java.util.UUID(1, 92))), 404));
        assertThat(foreign.get("message")).isEqualTo(absent.get("message"));
        assertThat(foreign.toString()).doesNotContain("Acompanante Ajeno").doesNotContain("888");
        var own = CatalogHttpTest.json(exchange(withToken(get(url), ADMIN), 200));
        assertThat(own.toString()).doesNotContain("Acompanante Ajeno").doesNotContain("888");
        assertThat(store.reservations.get(java.util.UUID.fromString(id)).companions()).hasSize(1);
    }

    @Test void theListOfAnotherTenantNeverShowsTheReservationOrItsCompanions() throws Exception {
        exchange(create(withCompanion(createBody()), "companion-tenant-4", ADMIN), 201);
        var page = CatalogHttpTest.json(exchange(asOtherTenant(get(BASE + "/reservations").param("scope", "all")), 200));
        assertThat(page.get("data")).isEmpty();
        assertThat(page.toString()).doesNotContain("Acompanante Ajeno").doesNotContain("888");
    }

    @Test void aKeyUsedByAnotherTenantReplaysNothingOfTheOwner() throws Exception {
        var created = CatalogHttpTest.json(exchange(create(withCompanion(createBody()), "companion-tenant-5", ADMIN), 201));
        var body = withCompanion(createBody());
        body.put("customerId", foreignCustomer.toString());
        var other = asOtherTenant(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(BASE + "/reservations"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body)).header("Idempotency-Key", "companion-tenant-5");
        var answer = CatalogHttpTest.json(exchange(other, 404));
        assertThat(answer.toString()).doesNotContain(created.get("reservationId").asText());
        assertThat(store.reservations).hasSize(1);
    }
}
