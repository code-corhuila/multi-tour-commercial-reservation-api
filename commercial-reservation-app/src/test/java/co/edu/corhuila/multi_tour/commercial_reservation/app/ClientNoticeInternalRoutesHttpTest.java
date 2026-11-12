package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxStatus;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-015 api T-07 (AC-15.12, AC-15.13, RN-09, RN-11) over every internal route of the story, the content of a notice and the three outbox routes:
// each one refuses a person's token and a missing token, and the log output of all the calls, answers and refusals holds no address, name, telephone,
// tour or content. The gateway never routing /internal/ belongs to its own repository. Every value is invented.
@SpringBootTest(properties = {"logging.level.co.edu.corhuila=TRACE", "logging.level.org.springframework.web=DEBUG"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, CustomerHttpFixture.class, ClientNoticeHttpFixture.class, OutboxHttpTest.Memory.class})
class ClientNoticeInternalRoutesHttpTest {
    static final UUID TENANT = UUID.fromString("0b6f2c3d-5e41-4a97-8d20-1f7a9c3e6b84");
    static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    static final String ADDRESS = "ana.perez.ficticia@example.com";
    static final String NAME = "Ana Perez Ficticia";
    static final String PHONE = "3009998877";
    static final String TOUR = "Caminata Ficticia del Cocora";
    @Autowired MockMvc mvc;
    @Autowired CustomerHttpFixture.MemoryCustomers customers;
    @Autowired ClientNoticeHttpFixture.MemoryNotices notices;
    @Autowired OutboxHttpTest.MemoryOutbox outbox;
    UUID customer, event;

    @BeforeEach void plant() {
        customers.clear();
        notices.clear();
        outbox.events.clear();
        customer = UUID.randomUUID();
        event = UUID.randomUUID();
        customers.save(Customer.registerLead(customer, TENANT, "CC1001", NAME, BirthDateOrAge.ofAge(30), new ContactInfo(ADDRESS, PHONE), true, NOW));
        notices.notices.put(event, new StoredNotice(event, TENANT, UUID.randomUUID(), customer, NoticeType.PROOF_APPROVED, TOUR,
                Instant.parse("2027-03-01T13:00:00Z"), 100_000L, 224_000L, null));
        outbox.events.put(event, new OutboxEventView(event, "ClientNotified", 1, TENANT, UUID.randomUUID(), Map.of("notificationType", "PROOF_APPROVED"),
                "trace-1", NOW.minusSeconds(60), null, OutboxStatus.PENDING, 0, null, null));
    }

    private String token(String role) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of(role));
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private List<MockHttpServletRequestBuilder> routes() {
        String base = "/api/v1/commercial-reservation/internal";
        return List.of(get(base + "/tenants/" + TENANT + "/client-notices/" + event), get(base + "/outbox"), post(base + "/outbox/" + event + "/published"),
                post(base + "/outbox/" + event + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{\"error\":\"boom\"}"));
    }

    @Test void everyInternalRouteRefusesAPersonTokenAndAMissingToken() throws Exception {
        for (String role : List.of("TENANT_ADMINISTRATOR", "OPERATIONAL_COLLABORATOR", "ACCOUNTANT", "FINAL_CUSTOMER")) {
            for (MockHttpServletRequestBuilder route : routes()) {
                mvc.perform(route.header("Authorization", "Bearer " + token(role))).andExpect(status().isForbidden());
            }
        }
        for (MockHttpServletRequestBuilder route : routes()) {
            mvc.perform(route).andExpect(status().isUnauthorized());
        }
        assertThat(outbox.events.get(event).attempts()).isZero();
        assertThat(outbox.events.get(event).status()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test void noLogLineCarriesAnAddressANameOrTheContent(CapturedOutput output) throws Exception {
        String service = "Bearer " + token("SERVICE");
        String base = "/api/v1/commercial-reservation/internal";
        mvc.perform(get(base + "/tenants/" + TENANT + "/client-notices/" + event).header("Authorization", service)).andExpect(status().isOk());
        mvc.perform(get(base + "/tenants/" + TENANT + "/client-notices/" + UUID.randomUUID()).header("Authorization", service)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/outbox").header("Authorization", service)).andExpect(status().isOk());
        mvc.perform(post(base + "/outbox/" + event + "/attempts").header("Authorization", service).contentType(MediaType.APPLICATION_JSON)
                .content("{\"error\":\"Rejected for " + ADDRESS + " " + NAME + " " + PHONE + "\"}")).andExpect(status().isOk());
        mvc.perform(post(base + "/outbox/" + event + "/published").header("Authorization", service)).andExpect(status().isOk());
        mvc.perform(get(base + "/tenants/" + TENANT + "/client-notices/" + event).header("Authorization", "Bearer " + token("TENANT_ADMINISTRATOR")))
                .andExpect(status().isForbidden());

        assertThat(output.getAll()).isNotBlank();
        assertThat(output.getAll()).doesNotContain(ADDRESS, "ana.perez", NAME, PHONE, TOUR, "100000", "224000");
    }
}
