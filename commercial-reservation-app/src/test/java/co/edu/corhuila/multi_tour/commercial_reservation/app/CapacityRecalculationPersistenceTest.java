package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

// RN-SAL-001 and H8 (ND-08, DP-D6-8) on the real schema: a PATCH of the tour capacity recomputes salida_capacity.effective_capacity from the
// stored vehicle total, in the transaction of the catalog update, and the seat hold judges the new value.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = CatalogHttpPersistenceTest.Database.class)
@Import(CatalogHttpPersistenceTest.FixedClock.class)
class CapacityRecalculationPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogServiceRepository repository;

    private CatalogService tour(UUID tenant, int capacity) {
        var item = CatalogService.register(UUID.randomUUID(), tenant, CatalogServiceType.TOUR, "Recalculated capacity tour",
                CommercialTerms.of(100L, 50L, capacity, false, false), TestJwtKeys.NOW);
        repository.save(item);
        return item;
    }

    private UUID row(UUID tenant, UUID service, String status, int effective, int held, Integer vehicles) {
        UUID salida = UUID.randomUUID();
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, effective_capacity, "
                + "held_seats, service_id, hora_inicio, status, vehicle_capacity_ceiling) VALUES (?, ?, ?, ?, ?, ?, now() + interval '30 days', ?, ?)",
                UUID.randomUUID(), tenant, salida, effective, held, service, status, vehicles);
        return salida;
    }

    private int effective(UUID tenant, UUID salida) {
        return jdbc.queryForObject("SELECT effective_capacity FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?",
                Integer.class, tenant, salida);
    }

    private org.springframework.test.web.servlet.MvcResult patchCapacity(UUID tenant, UUID service, int capacity) throws Exception {
        return mvc.perform(patch(CatalogHttpTest.BASE + "/" + service)
                .header("Authorization", "Bearer " + CatalogHttpTest.token(tenant.toString(), "TENANT_ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredCapacity\":" + capacity + "}")).andReturn();
    }

    @Test void aLowerTourCapacityLowersTheEffectiveCapacityAndTheHoldJudgesIt() throws Exception {
        UUID tenant = UUID.randomUUID();
        var item = tour(tenant, 20);
        UUID salida = row(tenant, item.serviceId(), "SCHEDULED", 20, 10, 20);

        status().isOk().match(patchCapacity(tenant, item.serviceId(), 15));

        assertThat(effective(tenant, salida)).isEqualTo(15);
        var holder = new JdbcSalidaCapacityHolder(jdbc);
        assertThat(holder.hold(tenant, salida, 6)).isFalse();
        assertThat(holder.hold(tenant, salida, 5)).isTrue();
    }

    @Test void aHigherTourCapacityRaisesTheEffectiveCapacityUpToTheVehicleTotal() throws Exception {
        UUID tenant = UUID.randomUUID();
        var item = tour(tenant, 15);
        UUID salida = row(tenant, item.serviceId(), "SCHEDULED", 15, 10, 20);

        status().isOk().match(patchCapacity(tenant, item.serviceId(), 18));
        assertThat(effective(tenant, salida)).isEqualTo(18);
        status().isOk().match(patchCapacity(tenant, item.serviceId(), 30));
        assertThat(effective(tenant, salida)).isEqualTo(20);
    }

    @Test void aTourCapacityBelowTheHeldSeatsIsRefusedAndTheEffectiveCapacityStays() throws Exception {
        UUID tenant = UUID.randomUUID();
        var item = tour(tenant, 20);
        UUID salida = row(tenant, item.serviceId(), "SCHEDULED", 20, 10, 20);

        var refused = patchCapacity(tenant, item.serviceId(), 9);

        status().isUnprocessableEntity().match(refused);
        assertThat(effective(tenant, salida)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT configured_capacity FROM commercial_reservation.catalog_service WHERE service_id = ?",
                Integer.class, item.serviceId())).isEqualTo(20);
    }

    @Test void aDepartureWithoutAVehicleTotalAndAFinalDepartureKeepTheirEffectiveCapacity() throws Exception {
        UUID tenant = UUID.randomUUID();
        var item = tour(tenant, 20);
        UUID unknown = row(tenant, item.serviceId(), "SCHEDULED", 20, 10, null);
        UUID finalized = row(tenant, item.serviceId(), "FINALIZED", 20, 0, 20);
        UUID otherService = row(tenant, tour(tenant, 20).serviceId(), "SCHEDULED", 20, 0, 20);

        status().isOk().match(patchCapacity(tenant, item.serviceId(), 15));

        assertThat(effective(tenant, unknown)).isEqualTo(20);
        assertThat(effective(tenant, finalized)).isEqualTo(20);
        assertThat(effective(tenant, otherService)).isEqualTo(20);
    }

    @Test void theMirrorRowKeepsTheVehicleTotalAndASaveDoesNotLoseIt() {
        UUID tenant = UUID.randomUUID();
        var item = tour(tenant, 20);
        var departures = new JdbcDepartureStateRepository(jdbc);
        UUID salida = UUID.randomUUID();
        Instant starts = Instant.now().truncatedTo(ChronoUnit.MICROS).plus(2, ChronoUnit.DAYS);

        departures.save(new DepartureState(salida, tenant, item.serviceId(), starts, DepartureStatus.SCHEDULED, 20, 0));
        assertThat(departures.find(tenant, salida).orElseThrow().vehicleCapacityCeiling()).isNull();

        departures.save(departures.find(tenant, salida).orElseThrow().withVehicleCapacityCeiling(14).withEffectiveCapacity(14));
        departures.save(departures.find(tenant, salida).orElseThrow().withStatus(DepartureStatus.IN_EXECUTION));

        var stored = departures.find(tenant, salida).orElseThrow();
        assertThat(stored.vehicleCapacityCeiling()).isEqualTo(14);
        assertThat(stored.effectiveCapacity()).isEqualTo(14);
    }
}
