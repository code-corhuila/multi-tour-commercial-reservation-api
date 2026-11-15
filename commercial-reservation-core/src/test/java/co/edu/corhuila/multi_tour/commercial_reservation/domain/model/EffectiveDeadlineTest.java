package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// T-01 of HU-COM-011: RN-01, RN-02; AC-11.6. The effective deadline is the lower of the due instant plus the extension and the
// departure start; nothing moves it past that start (RN-RES-006, RN-EJE-001). Pure: the inputs are instants and hours.
class EffectiveDeadlineTest {

    private static final Instant DUE = Instant.parse("2026-11-10T10:00:00Z");

    @Test
    void effectiveDeadlineIsTheLowerOfDueWithExtensionAndDepartureStart() {
        Instant farStart = DUE.plus(Duration.ofDays(30));
        Instant nearStart = DUE.plus(Duration.ofHours(5));

        assertThat(EffectiveDeadline.of(DUE, 0, farStart)).isEqualTo(DUE);
        assertThat(EffectiveDeadline.of(DUE, 3, farStart)).isEqualTo(DUE.plus(Duration.ofHours(3)));
        assertThat(EffectiveDeadline.of(DUE, 3, nearStart)).isEqualTo(DUE.plus(Duration.ofHours(3)));
        assertThat(EffectiveDeadline.of(DUE, 48, nearStart)).isEqualTo(nearStart);
    }

    @Test
    void noDeadlineEverPassesTheDepartureStart() {
        Instant start = DUE.minus(Duration.ofHours(2));

        assertThat(EffectiveDeadline.of(DUE, 0, start)).isEqualTo(start);
        assertThat(EffectiveDeadline.of(DUE, 720, start)).isEqualTo(start);
        assertThat(EffectiveDeadline.of(DUE, 24, null)).isEqualTo(DUE.plus(Duration.ofHours(24)));
    }
}
