package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.InternalDepartureReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.InternalDepartureReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InternalDepartureReservationQueries;
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

// G-API6-1 over HTTP (RN-IDR-01 to RN-IDR-05): the route operations-cost calls with its SERVICE token. The store is a fake; the real statements are proved
// in InternalDepartureReservationQueriesPersistenceTest. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, InternalDepartureReservationsHttpTest.Fixture.class})
class InternalDepartureReservationsHttpTest {
    @TestConfiguration
    static class Fixture {
        @Bean @Primary MemoryInternalReservations memoryInternalReservations() { return new MemoryInternalReservations(); }
    }

    static final class MemoryInternalReservations implements InternalDepartureReservationQueries {
        final List<List<Object>> asked = new ArrayList<>();
        boolean missing;

        @Override public Optional<InternalDepartureReservationPage> ofDeparture(UUID tenantId, UUID salidaId, int page, int limit) {
            asked.add(List.of(tenantId, salidaId, page, limit));
            if (missing) return Optional.empty();
            return Optional.of(new InternalDepartureReservationPage(List.of(
                    new InternalDepartureReservation(new UUID(7, 1), ReservationStatus.CONFIRMED, 3, new UUID(7, 2), 150_000L)), 41));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MemoryInternalReservations store;
    final UUID tenant = UUID.randomUUID(), departure = UUID.randomUUID();

    @BeforeEach void clear() { store.asked.clear(); store.missing = false; }

    MvcResult call(String query, String role, int code) throws Exception {
        var request = get("/api/v1/commercial-reservation/internal/tenants/" + tenant + "/departures/" + departure + "/reservations" + query)
                .header("X-Correlation-Id", CatalogHttpTest.TRACE);
        if (role != null) request.header("Authorization", "Bearer " + CustomerHttpTest.token(null, role, List.of()));
        MvcResult result = mvc.perform(request).andReturn();
        status().is(code).match(result);
        return result;
    }

    @Test void theServiceGetsTheRowsPaginatedWithTheFiveFieldsOfTheContractAndNoPersonalData() throws Exception {
        JsonNode body = CatalogHttpTest.json(call("?page=2&limit=5", "SERVICE", 200));
        var row = body.get("data").get(0);
        List<String> names = new ArrayList<>();
        row.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("reservationId", "status", "travelers", "customerId", "validatedAmountCents");
        assertThat(row.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(row.get("travelers").asInt()).isEqualTo(3);
        assertThat(row.get("validatedAmountCents").asLong()).isEqualTo(150_000L);
        assertThat(body.at("/meta/total").asInt()).isEqualTo(41);
        assertThat(body.at("/meta/page").asInt()).isEqualTo(2);
        assertThat(store.asked).containsExactly(List.of(tenant, departure, 2, 5));
    }

    @Test void aPersonGets403AndNoTokenIs401() throws Exception {
        call("", "TENANT_ADMINISTRATOR", 403);
        call("", "OPERATIONAL_COLLABORATOR", 403);
        call("", null, 401);
        assertThat(store.asked).isEmpty();
    }

    @Test void aMissingDepartureIs404AndABadQueryIs400() throws Exception {
        store.missing = true;
        call("", "SERVICE", 404);
        store.missing = false;
        call("?status=CONFIRMED", "SERVICE", 400);
        call("?limit=101", "SERVICE", 400);
    }
}
