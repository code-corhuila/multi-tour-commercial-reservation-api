package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Shared by the HTTP tests of HU-COM-007 (api spec T-06, T-07, T-09): one tenant with a customer, a tour and two departures.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, CustomerHttpFixture.class, DiscountRuleHttpFixture.class, ReservationHttpFixture.class})
abstract class ReservationHttpBase {
    static final String BASE = "/api/v1/commercial-reservation";
    static final String TRACE = CatalogHttpTest.TRACE;
    static final String ADMIN = "TENANT_ADMINISTRATOR", COLLABORATOR = "OPERATIONAL_COLLABORATOR", MANAGER = "MANAGER",
            ACCOUNTANT = "ACCOUNTANT", ANALYST = "ANALYST", FINAL_CUSTOMER = "FINAL_CUSTOMER";
    static final Instant NOW = TestJwtKeys.NOW;
    final UUID tenant = UUID.fromString(TestJwtKeys.TENANT), otherTenant = new UUID(0, 999);
    final UUID customer = new UUID(1, 1), tour = new UUID(1, 2), otherTour = new UUID(1, 3), departure = new UUID(1, 4),
            fullDeparture = new UUID(1, 5);
    @Autowired MockMvc mvc;
    @Autowired ReservationHttpFixture.MemoryReservations store;
    @Autowired TenantDirectoryHttpFixture.MemoryDirectory directory;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;
    @Autowired CatalogHttpFixture.MemoryKeys keys;
    @Autowired CustomerHttpFixture.MemoryCustomers customers;

    @BeforeEach void seed() {
        store.clear(); directory.tenants.clear(); keys.keys.clear(); catalog.items.clear(); customers.clear();
        directory.tenants.put(tenant.toString(), new TenantRef(tenant, true));
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio",
                CommercialTerms.of(100_000L, 40_000L, 20, false, false), NOW));
        catalog.save(CatalogService.register(otherTour, otherTenant, CatalogServiceType.TOUR, "Tour Ajeno",
                CommercialTerms.of(100_000L, 40_000L, 20, false, false), NOW));
        customers.save(Customer.registerLead(customer, tenant, "CC12345", "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("persona@example.invalid", "3000000000"), true, NOW));
        store.departures.put(departure, new DepartureState(departure, tenant, tour, NOW.plusSeconds(86_400),
                DepartureStatus.SCHEDULED, 10, 7));
        store.departures.put(fullDeparture, new DepartureState(fullDeparture, tenant, tour, NOW.plusSeconds(172_800),
                DepartureStatus.SCHEDULED, 4, 4));
    }

    static String token(String role) throws Exception { return CatalogHttpTest.token(TestJwtKeys.TENANT, role); }

    MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String role) throws Exception {
        return request.header("Authorization", "Bearer " + token(role)).header("X-Correlation-Id", TRACE);
    }

    MvcResult exchange(MockHttpServletRequestBuilder request, int code) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        status().is(code).match(result);
        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isEqualTo(TRACE);
        return result;
    }

    Map<String, Object> quoteBody(int people) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceId", tour.toString()); body.put("salidaId", departure.toString()); body.put("people", people);
        return body;
    }

    Map<String, Object> createBody() {
        var body = quoteBody(3);
        body.put("customerId", customer.toString()); body.put("paymentType", "DEPOSIT"); body.put("paymentMethod", "TRANSFER");
        return body;
    }

    MockHttpServletRequestBuilder quote(Map<String, Object> body, String role) throws Exception {
        return withToken(post(BASE + "/reservation-quotes"), role).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
    }

    MockHttpServletRequestBuilder create(Map<String, Object> body, String key, String role) throws Exception {
        var request = withToken(post(BASE + "/reservations"), role).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }
}
