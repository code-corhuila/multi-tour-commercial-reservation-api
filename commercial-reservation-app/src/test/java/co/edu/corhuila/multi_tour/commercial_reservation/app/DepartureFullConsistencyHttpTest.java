package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-008: the list of departures marks `full` as the public tour does (available seats at or below zero), so a hold above the
// capacity still reads as full.
class DepartureFullConsistencyHttpTest extends ReservationHttpBase {

    @Test void aDepartureHeldAboveItsCapacityReadsAsFull() throws Exception {
        UUID oversold = new UUID(1, 6);
        store.departures.put(oversold, new DepartureState(oversold, tenant, tour, NOW.plusSeconds(259_200), DepartureStatus.SCHEDULED, 4, 6));
        var page = CatalogHttpTest.json(exchange(withToken(get(BASE + "/catalog/services/" + tour + "/departures"), ADMIN), 200));
        boolean seen = false;
        for (var row : page.get("data")) {
            if (row.get("departureId").asText().equals(oversold.toString())) {
                seen = true;
                assertThat(row.get("full").asBoolean()).isTrue();
            }
        }
        assertThat(seen).isTrue();
    }
}
