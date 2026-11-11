package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// The instant a person is shown (HU-COM-009 quote and payment answer): the one the service judges (RN-02, RN-17).
class PaymentDeadlineInForceTest {

    private static final Instant DUE = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-11-03T10:00:00Z");

    @Test
    void withoutExtensionItIsTheStoredInstant() {
        assertThat(new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE).inForce(START)).isEqualTo(DUE);
    }

    @Test
    void theExtensionCountsButNeverPassesTheDepartureStart() {
        UUID by = UUID.randomUUID();
        assertThat(new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE, 2, DUE, by, null, null).inForce(START)).isEqualTo(DUE.plusSeconds(7_200));
        assertThat(new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE, 100, DUE, by, null, null).inForce(START)).isEqualTo(START);
    }

    @Test
    void aFrozenDeadlineHasNoRunningInstant() {
        assertThat(new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE, null, null, null, DUE, 90).inForce(START)).isNull();
    }
}
