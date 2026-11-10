package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// HU-COM-012 amendment 2026-11-10, R1 (AC-12.R1.1 to AC-12.R1.7) over HTTP with every real JDBC bean: the saga change-departure-vehicles lowers the
// ceiling of a departure and restores it. Two tenants share the database, so every case also checks that the other one is untouched.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
@Import(CreditBalanceWriteJdbcHttpTest.FixedClock.class)
class DepartureCeilingJdbcHttpTest {
    static final String BASE = "/api/v1/commercial-reservation/internal/tenants/";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogServiceRepository catalog;

    final UUID tenant = UUID.randomUUID(), other = UUID.randomUUID(), departure = UUID.randomUUID(), otherDeparture = UUID.randomUUID();
    String service;

    @BeforeEach void seed() throws Exception {
        service = CustomerHttpTest.token(null, "SERVICE", List.of());
        plant(tenant, departure, "SCHEDULED", 10, 4, "ACTIVE");
        plant(other, otherDeparture, "SCHEDULED", 10, 4, "INACTIVE");
    }

    void plant(UUID forTenant, UUID forDeparture, String status, int ceiling, int held, String tenantStatus) {
        OffsetDateTime now = OffsetDateTime.ofInstant(TestJwtKeys.NOW, ZoneOffset.UTC);
        jdbc.update("INSERT INTO commercial_reservation.tenant_status (tenant_id, identifier, status, time_zone, country, last_event_occurred_at, created_at, "
                + "updated_at) VALUES (?, ?, ?, 'America/Bogota', 'CO', ?, ?, ?) ON CONFLICT DO NOTHING", forTenant, "t-" + forTenant, tenantStatus, now, now, now);
        UUID tour = UUID.randomUUID();
        catalog.save(CatalogService.register(tour, forTenant, CatalogServiceType.TOUR, "Tour Ficticio", CommercialTerms.of(100_000L, 40_000L, 20, false, false),
                TestJwtKeys.NOW));
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, hora_inicio, status, effective_capacity, "
                + "held_seats) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), forTenant, forDeparture, tour, now.plusDays(1), status, ceiling, held);
    }

    MvcResult call(UUID forTenant, UUID forDeparture, String key, String body, String bearer, int code) throws Exception {
        var request = put(BASE + forTenant + "/departures/" + forDeparture + "/capacity-ceiling").header("Authorization", "Bearer " + bearer)
                .header("X-Correlation-Id", CatalogHttpTest.TRACE).contentType(MediaType.APPLICATION_JSON).content(body);
        MvcResult result = mvc.perform(key == null ? request : request.header("Idempotency-Key", key)).andReturn();
        status().is(code).match(result);
        return result;
    }

    JsonNode ok(String key, int ceiling) throws Exception {
        return CatalogHttpTest.json(call(tenant, departure, key, "{\"ceiling\":" + ceiling + "}", service, 200));
    }

    int ceilingOf(UUID forTenant, UUID forDeparture) {
        return jdbc.queryForObject("SELECT effective_capacity FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?", Integer.class,
                forTenant, forDeparture);
    }

    @Test void AC_12_R1_1_theServiceLowersTheCeilingAndTheOtherTenantIsUntouched() throws Exception {
        var answer = ok("ceiling-key-0001", 6);
        assertThat(answer.get("previousCeiling").asInt()).isEqualTo(10);
        assertThat(answer.get("ceiling").asInt()).isEqualTo(6);
        assertThat(answer.get("seatsHeld").asInt()).isEqualTo(4);
        assertThat(ceilingOf(tenant, departure)).isEqualTo(6);
        assertThat(ceilingOf(other, otherDeparture)).isEqualTo(10);
    }

    @Test void AC_12_R1_2_moreSeatsHeldThanTheCeilingIs422ChangesNothingAndTheKeyCanBeUsedLater() throws Exception {
        var refused = call(tenant, departure, "ceiling-key-0002", "{\"ceiling\":3}", service, 422);
        assertThat(CatalogHttpTest.json(refused).get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(ceilingOf(tenant, departure)).isEqualTo(10);
        assertThat(ok("ceiling-key-0002", 5).get("ceiling").asInt()).isEqualTo(5);
    }

    @Test void AC_12_R1_3_theCompensationRestoresThePreviousCeiling() throws Exception {
        int previous = ok("ceiling-key-0003", 6).get("previousCeiling").asInt();
        var restored = ok("ceiling-key-0004", previous);
        assertThat(restored.get("previousCeiling").asInt()).isEqualTo(6);
        assertThat(ceilingOf(tenant, departure)).isEqualTo(10);
    }

    @Test void AC_12_R1_4_aReplayAnswersTheOriginalAndTheSameKeyWithAnotherBodyIs422() throws Exception {
        var first = ok("ceiling-key-0005", 6);
        ok("ceiling-key-0006", 10);
        var replay = ok("ceiling-key-0005", 6);
        assertThat(replay.get("previousCeiling").asInt()).isEqualTo(first.get("previousCeiling").asInt());
        assertThat(replay.get("ceiling").asInt()).isEqualTo(6);
        assertThat(ceilingOf(tenant, departure)).isEqualTo(10);
        call(tenant, departure, "ceiling-key-0005", "{\"ceiling\":7}", service, 422);
        assertThat(ceilingOf(tenant, departure)).isEqualTo(10);
    }

    @Test void AC_12_R1_5_onlyTheServiceRoleWithAValidKeyAndBodyIsAccepted() throws Exception {
        call(tenant, departure, "ceiling-key-0007", "{\"ceiling\":6}", CatalogHttpTest.token(tenant.toString(), "TENANT_ADMINISTRATOR"), 403);
        call(tenant, departure, null, "{\"ceiling\":6}", service, 400);
        call(tenant, departure, "short", "{\"ceiling\":6}", service, 400);
        call(tenant, departure, "ceiling-key-0007", "{}", service, 400);
        call(tenant, departure, "ceiling-key-0007", "{\"ceiling\":-1}", service, 400);
        call(tenant, departure, "ceiling-key-0007", "{\"ceiling\":\"six\"}", service, 400);
        call(tenant, departure, "ceiling-key-0007", "{\"ceiling\":6,\"tenantId\":\"" + other + "\"}", service, 400);
        assertThat(ceilingOf(tenant, departure)).isEqualTo(10);
    }

    @Test void AC_12_R1_6_aDepartureOfAnotherTenantLooksMissingAndAFinalOneIsRefused() throws Exception {
        call(tenant, otherDeparture, "ceiling-key-0008", "{\"ceiling\":6}", service, 404);
        call(other, departure, "ceiling-key-0009", "{\"ceiling\":6}", service, 404);
        call(tenant, UUID.randomUUID(), "ceiling-key-0010", "{\"ceiling\":6}", service, 404);
        assertThat(ceilingOf(tenant, departure)).isEqualTo(10);
        assertThat(ceilingOf(other, otherDeparture)).isEqualTo(10);
        UUID finalized = UUID.randomUUID();
        plant(tenant, finalized, "FINALIZED", 10, 0, "ACTIVE");
        call(tenant, finalized, "ceiling-key-0011", "{\"ceiling\":6}", service, 422);
        assertThat(ceilingOf(tenant, finalized)).isEqualTo(10);
    }

    @Test void AC_12_R1_7_theRouteWorksForAnInactiveTenantAndTheKeyOfOneTenantIsNotTheKeyOfAnother() throws Exception {
        var inactive = CatalogHttpTest.json(call(other, otherDeparture, "ceiling-key-0012", "{\"ceiling\":6}", service, 200));
        assertThat(inactive.get("previousCeiling").asInt()).isEqualTo(10);
        assertThat(ok("ceiling-key-0012", 8).get("previousCeiling").asInt()).isEqualTo(10);
        assertThat(ceilingOf(other, otherDeparture)).isEqualTo(6);
        assertThat(ceilingOf(tenant, departure)).isEqualTo(8);
    }
}
