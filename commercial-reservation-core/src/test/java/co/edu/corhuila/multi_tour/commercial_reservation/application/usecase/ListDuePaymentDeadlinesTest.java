package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueDeadlinePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DuePaymentQueries;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-06 of HU-COM-011 (the job side): the list the job asks for is the question of the tenant of the path at the instant of the clock,
// and the service decides nothing here; the decision is the expiry (AC-11.2, AC-11.12).
class ListDuePaymentDeadlinesTest {

    private static final UUID TENANT = new UUID(11, 0xe1);
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    @Test
    void theListIsAskedForTheTenantOfThePathAtTheInstantOfTheClock() {
        List<String> asked = new ArrayList<>();
        DuePaymentQueries queries = (tenantId, now, page, limit) -> {
            asked.add(tenantId + ":" + now + ":" + page + ":" + limit);
            return new DueDeadlinePage(List.of(), 0);
        };

        var page = new ListDuePaymentDeadlinesUseCase(queries, Clock.fixed(NOW, ZoneOffset.UTC)).list(TENANT, 2, 50);

        assertThat(page.total()).isZero();
        assertThat(asked).containsExactly(TENANT + ":" + NOW + ":2:50");
    }
}
