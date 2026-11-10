package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// HU-COM-012 amendment 2026-11-10, R2 over HTTP (AC-12.R2.1 to AC-12.R2.5): the route operations-cost calls with the token of the person. The store is a fake;
// the real statements are proved in DepartureReservationQueriesPersistenceTest.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, DepartureReservationsHttpTest.Fixture.class})
class DepartureReservationsHttpTest {
    @TestConfiguration
    static class Fixture {
        @Bean @Primary MemoryDepartureReservations memoryDepartureReservations() { return new MemoryDepartureReservations(); }
    }

    static final class MemoryDepartureReservations implements DepartureReservationQueries {
        final List<List<Object>> asked = new ArrayList<>();
        boolean missing;

        @Override public Optional<DepartureReservationPage> ofDeparture(UUID tenantId, UUID serviceId, UUID salidaId, int page, int limit) {
            asked.add(List.of(tenantId, serviceId, salidaId, page, limit));
            if (missing) return Optional.empty();
            return Optional.of(new DepartureReservationPage(List.of(new DepartureReservation(new UUID(7, 1), "Persona Ficticia", 3, ReservationStatus.CONFIRMED)), 41));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MemoryDepartureReservations store;
    final UUID tenant = UUID.randomUUID(), service = UUID.randomUUID(), departure = UUID.randomUUID();

    @BeforeEach void clear() { store.asked.clear(); store.missing = false; }

    MvcResult call(String query, String bearer, int code) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/commercial-reservation/catalog/services/" + service + "/departures/" + departure + "/reservations" + query)
                .header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    String token(String role, String... permissions) throws Exception {
        return CustomerHttpTest.token(tenant.toString(), role, List.of(permissions));
    }

    @Test void AC_12_R2_1_theAdministratorGetsTheRowsAndOnlyTheFourFieldsOfTheContract() throws Exception {
        JsonNode body = CatalogHttpTest.json(call("?page=2&limit=5", token("TENANT_ADMINISTRATOR"), 200));
        var row = body.get("data").get(0);
        List<String> names = new ArrayList<>();
        row.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("reservationId", "customerName", "people", "reservationStatus");
        assertThat(row.get("reservationStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(body.at("/meta/total").asInt()).isEqualTo(41);
        assertThat(store.asked).containsExactly(List.of(tenant, service, departure, 2, 5));
    }

    @Test void AC_12_R2_4_aCollaboratorWithThePermissionReadsAndTheOthersAreRefused() throws Exception {
        call("", token("OPERATIONAL_COLLABORATOR", "MANAGE_DEPARTURE_EXECUTION"), 200);
        call("", token("OPERATIONAL_COLLABORATOR"), 403);
        call("", token("MANAGER", "MANAGE_DEPARTURE_EXECUTION"), 403);
        call("", token("ANALYST"), 403);
        call("", token("ACCOUNTANT"), 403);
        call("", token("FINAL_CUSTOMER"), 403);
        call("", CustomerHttpTest.token(null, "SERVICE", List.of()), 403);
        assertThat(store.asked).hasSize(1);
    }

    @Test void AC_12_R2_3_aMissingDepartureIs404AndAnUnknownQueryParameterIs400() throws Exception {
        store.missing = true;
        call("", token("TENANT_ADMINISTRATOR"), 404);
        store.missing = false;
        call("?status=CONFIRMED", token("TENANT_ADMINISTRATOR"), 400);
        call("?limit=101", token("TENANT_ADMINISTRATOR"), 400);
    }
}
