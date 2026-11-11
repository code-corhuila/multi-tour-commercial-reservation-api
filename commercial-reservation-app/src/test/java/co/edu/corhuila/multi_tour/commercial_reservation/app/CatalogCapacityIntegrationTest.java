package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = CatalogHttpPersistenceTest.Database.class)
@Import(CatalogHttpPersistenceTest.FixedClock.class)
class CatalogCapacityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogServiceRepository repository;

    @Test void capacityBelowHeldSeatsIsRejectedAndNothingIsWritten() throws Exception {
        UUID tenant = UUID.randomUUID();
        var item = CatalogService.register(UUID.randomUUID(), tenant, CatalogServiceType.TOUR, "Persisted capacity tour",
                CommercialTerms.of(100L, 50L, 18, false, false), TestJwtKeys.NOW.minusSeconds(2));
        item.inactivate(UUID.fromString(TestJwtKeys.ACCOUNT), TestJwtKeys.NOW.minusSeconds(1));
        repository.save(item);
        item.reactivate(UUID.fromString(TestJwtKeys.ACCOUNT), TestJwtKeys.NOW);
        repository.save(item);
        row(tenant, item.serviceId(), 5); row(tenant, item.serviceId(), 12);
        // The simple FK permits a mismatched tenant; that row must not affect this service's maximum (DB DD-6).
        UUID foreignTenant = UUID.randomUUID();
        row(foreignTenant, item.serviceId(), 100);
        Map<String, Object> original = stored(tenant, item.serviceId());
        var events = events(tenant, item.serviceId());
        assertThat(events).hasSize(2);
        var refused = mvc.perform(patch(CatalogHttpTest.BASE + "/" + item.serviceId())
                .header("Authorization", "Bearer " + CatalogHttpTest.token(tenant.toString(), "TENANT_ADMINISTRATOR"))
                .header("X-Correlation-Id", CatalogHttpTest.TRACE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"commercialName\":\"Must roll back\",\"configuredCapacity\":10}")).andReturn();
        status().isUnprocessableEntity().match(refused);
        new CatalogContract().validate(refused, false);
        CatalogHttpTest.assertError(refused, "BUSINESS_RULE_VIOLATION",
                "The capacity cannot be lower than the seats already held in a scheduled departure");
        assertThat(stored(tenant, item.serviceId())).isEqualTo(original);
        assertThat(events(tenant, item.serviceId())).isEqualTo(events);
        var accepted = mvc.perform(patch(CatalogHttpTest.BASE + "/" + item.serviceId())
                .header("Authorization", "Bearer " + CatalogHttpTest.token(tenant.toString(), "TENANT_ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredCapacity\":12}")).andReturn();
        status().isOk().match(accepted);
        new CatalogContract().validate(accepted, false);
        assertThat(stored(tenant, item.serviceId()).get("configured_capacity")).isEqualTo(12);
        assertThat(events(tenant, item.serviceId())).isEqualTo(events);
        var foreign = mvc.perform(patch(CatalogHttpTest.BASE + "/" + item.serviceId())
                .header("Authorization", "Bearer " + CatalogHttpTest.token(foreignTenant.toString(), "TENANT_ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredCapacity\":1}")).andReturn();
        status().isNotFound().match(foreign);
        new CatalogContract().validate(foreign, false);
    }

    @Test void noRowsForThisServiceMeansZeroDespiteOtherServicesAndTenants() throws Exception {
        UUID tenant = UUID.randomUUID();
        var empty = CatalogService.register(UUID.randomUUID(), tenant, CatalogServiceType.TOUR, "No departures",
                CommercialTerms.of(100L, 50L, 18, false, false), TestJwtKeys.NOW);
        var other = CatalogService.register(UUID.randomUUID(), tenant, CatalogServiceType.TOUR, "Other departures",
                CommercialTerms.of(100L, 50L, 18, false, false), TestJwtKeys.NOW);
        repository.save(empty); repository.save(other);
        row(tenant, other.serviceId(), 100);
        row(UUID.randomUUID(), empty.serviceId(), 100);
        var accepted = mvc.perform(patch(CatalogHttpTest.BASE + "/" + empty.serviceId())
                .header("Authorization", "Bearer " + CatalogHttpTest.token(tenant.toString(), "TENANT_ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredCapacity\":1}")).andReturn();
        status().isOk().match(accepted);
        new CatalogContract().validate(accepted, false);
        assertThat(stored(tenant, empty.serviceId()).get("configured_capacity")).isEqualTo(1);
    }

    private void row(UUID tenant, UUID service, int held) {
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity " +
                "(salida_capacity_id, tenant_id, salida_id, effective_capacity, held_seats, service_id, hora_inicio, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, now() + interval '30 days', 'SCHEDULED')",
                UUID.randomUUID(), tenant, UUID.randomUUID(), Math.max(18, held), held, service);
    }
    private Map<String, Object> stored(UUID tenant, UUID service) {
        return jdbc.queryForMap("SELECT * FROM commercial_reservation.catalog_service WHERE tenant_id = ? AND service_id = ?",
                tenant, service);
    }
    private List<Map<String, Object>> events(UUID tenant, UUID service) {
        return jdbc.queryForList("SELECT * FROM commercial_reservation.outbox_event " +
                "WHERE tenant_id = ? AND service_id = ? ORDER BY outbox_event_id", tenant, service);
    }
}
