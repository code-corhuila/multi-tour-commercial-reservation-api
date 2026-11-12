package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-08 of HU-COM-020 (-api): RN-13; AC-20.17. The deadline read carries frozen, the cause and the remaining time, so a screen can show it suspended without counting it; an unfrozen one carries
// none of them. The names are PROPOSED (the contract change is the owner draft of T-00).
class DeadlineResponseFreezeTest {

    private static final Instant DUE = Instant.parse("2026-11-12T12:00:00Z");

    @Test
    void aFrozenDeadlineCarriesItsCauseAndTheRemainingTime() {
        var frozen = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE).frozen(Instant.parse("2026-11-12T08:00:00Z"), null, FreezeCause.TENANT_INACTIVE);

        DeadlineResponse response = DeadlineResponse.of(new DeadlineView(UUID.randomUUID(), frozen, null, DUE));

        assertThat(response.frozen()).isTrue();
        assertThat(response.freezeCause()).isEqualTo("TENANT_INACTIVE");
        assertThat(response.remainingAtFreezeMinutes()).isEqualTo(240);
    }

    @Test
    void anUnfrozenDeadlineCarriesNoFreezeFields() {
        DeadlineResponse response = DeadlineResponse.of(new DeadlineView(UUID.randomUUID(), new PaymentDeadline(DeadlineKind.BALANCE, DUE), null, DUE));

        assertThat(response.frozen()).isFalse();
        assertThat(response.freezeCause()).isNull();
        assertThat(response.remainingAtFreezeMinutes()).isNull();
    }
}
