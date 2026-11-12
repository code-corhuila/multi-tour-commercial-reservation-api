package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantIdPage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// The list the jobs ask for (HU-WRK-001 DP-08): the active tenants of the local copy, ids only, one page at a time.
class ListActiveTenantsTest {

    @Test
    void thePageAndTheLimitReachTheStoreAndTheAnswerIsTheStoresAnswer() {
        List<String> asked = new ArrayList<>();
        UUID one = new UUID(21, 1);
        var use = new ListActiveTenantsUseCase((page, limit) -> {
            asked.add(page + ":" + limit);
            return new TenantIdPage(List.of(one), 41);
        });

        var answer = use.list(3, 20);

        assertThat(asked).containsExactly("3:20");
        assertThat(answer.items()).containsExactly(one);
        assertThat(answer.total()).isEqualTo(41);
    }
}
