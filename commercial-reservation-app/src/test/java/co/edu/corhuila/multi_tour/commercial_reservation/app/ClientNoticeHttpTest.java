package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.StoredNotice;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-015 api T-05 over the route getClientNoticeContent of the contract (AC-15.9, AC-15.11, AC-15.12): the SERVICE token of job 4 reads the
// recipient and the variables of one notice; a person gets 403, another tenant or an unknown event gets 404. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, CustomerHttpFixture.class, ClientNoticeHttpFixture.class})
class ClientNoticeHttpTest {
    static final UUID TENANT = UUID.fromString("0b6f2c3d-5e41-4a97-8d20-1f7a9c3e6b84");
    static final UUID OTHER_TENANT = UUID.fromString("7c1e9a42-3b58-4d06-9f12-6a8d0e4b2c75");
    static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired CustomerHttpFixture.MemoryCustomers customers;
    @Autowired ClientNoticeHttpFixture.MemoryNotices notices;
    UUID customer, event, reservation;

    @BeforeEach void plant() {
        customers.clear();
        notices.clear();
        customer = UUID.randomUUID();
        event = UUID.randomUUID();
        reservation = UUID.randomUUID();
        customers.save(Customer.registerLead(customer, TENANT, "CC1001", "Lead Person", BirthDateOrAge.ofAge(30),
                new ContactInfo("lead@example.com", "3000000000"), true, NOW));
        notices.notices.put(event, new StoredNotice(event, TENANT, reservation, customer, NoticeType.PROOF_APPROVED, "Caminata Ficticia",
                Instant.parse("2027-03-01T13:00:00Z"), 100_000L, 224_000L, null));
    }

    private static String route(UUID tenant, UUID id) {
        return "/api/v1/commercial-reservation/internal/tenants/" + tenant + "/client-notices/" + id;
    }

    private String token(String role) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of(role));
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private MockHttpServletRequestBuilder ask(UUID tenant, UUID id, String role) throws Exception {
        MockHttpServletRequestBuilder request = get(route(tenant, id)).header("X-Correlation-Id", "client-notice-test");
        return role == null ? request : request.header("Authorization", "Bearer " + token(role));
    }

    @Test void jobFourReadsTheContentWithAServiceTokenOnly() throws Exception {
        mvc.perform(ask(TENANT, event, "SERVICE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.toString()))
                .andExpect(jsonPath("$.notificationType").value("PROOF_APPROVED"))
                .andExpect(jsonPath("$.recipientEmail").value("lead@example.com"))
                .andExpect(jsonPath("$.recipientName").value("Lead Person"))
                .andExpect(jsonPath("$.reservationId").value(reservation.toString()))
                .andExpect(jsonPath("$.tourName").value("Caminata Ficticia"))
                .andExpect(jsonPath("$.amountCents").value(100_000))
                .andExpect(jsonPath("$.pendingBalanceCents").value(224_000))
                .andExpect(jsonPath("$.causeCode").doesNotExist());
        mvc.perform(ask(TENANT, event, "TENANT_ADMINISTRATOR")).andExpect(status().isForbidden());
        mvc.perform(ask(TENANT, event, "OPERATIONAL_COLLABORATOR")).andExpect(status().isForbidden());
        mvc.perform(ask(TENANT, event, null)).andExpect(status().isUnauthorized());
    }

    @Test void aNoticeWithAnotherTenantInThePathAnswersNotFound() throws Exception {
        mvc.perform(ask(OTHER_TENANT, event, "SERVICE")).andExpect(status().isNotFound());
        mvc.perform(ask(TENANT, UUID.randomUUID(), "SERVICE")).andExpect(status().isNotFound());
    }

    @Test void aCustomerWithoutEmailHasNoContentToSend() throws Exception {
        customers.save(Customer.registerLead(customer, TENANT, "CC1001", "Lead Person", BirthDateOrAge.ofAge(30),
                new ContactInfo(null, "3000000000"), true, NOW));
        mvc.perform(ask(TENANT, event, "SERVICE")).andExpect(status().isNotFound());
    }
}
