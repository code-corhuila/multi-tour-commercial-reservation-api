package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-06 of HU-COM-020 (-api): RN-08; AC-20.9, AC-20.11. Pure, with the instant as an argument.
class EconomicCancellationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private static DepartureState departure(DepartureStatus status, Instant startsAt) {
        return new DepartureState(new UUID(20, 1), new UUID(20, 2), new UUID(20, 3), startsAt, status, 10, 2);
    }

    @Test
    void aDepartureCancelledDuringTheInactivationIsNotAttributableToTheCustomer() {
        DepartureState cancelled = departure(DepartureStatus.CANCELLED, NOW.plus(Duration.ofDays(20)));

        assertThat(EconomicCancellationPolicy.causeFor(cancelled, true, NOW)).isEqualTo(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER);
        assertThat(EconomicCancellationPolicy.allows(cancelled, true, NOW)).isFalse();
    }

    @Test
    void aReachedDepartureBarsTheCancellationForEconomicNonCompliance() {
        DepartureState reached = departure(DepartureStatus.SCHEDULED, NOW.minus(Duration.ofMinutes(1)));

        assertThat(EconomicCancellationPolicy.allows(reached, true, NOW)).isFalse();
        assertThat(EconomicCancellationPolicy.causeFor(reached, true, NOW)).isEqualTo(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER);
        assertThat(EconomicCancellationPolicy.allows(departure(DepartureStatus.SCHEDULED, NOW), true, NOW)).as("the scheduled time itself is reached").isFalse();
    }

    @Test
    void aScheduledDepartureInTheFutureOrAnActiveTenantKeepsTheOrdinaryRule() {
        DepartureState future = departure(DepartureStatus.SCHEDULED, NOW.plus(Duration.ofDays(5)));
        DepartureState cancelled = departure(DepartureStatus.CANCELLED, NOW.plus(Duration.ofDays(5)));

        assertThat(EconomicCancellationPolicy.allows(future, true, NOW)).isTrue();
        assertThat(EconomicCancellationPolicy.causeFor(future, true, NOW)).isEqualTo(CancellationCause.PAYMENT_EXPIRY);
        assertThat(EconomicCancellationPolicy.allows(cancelled, false, NOW)).as("outside an inactivation the rule of HU-COM-010 stands").isTrue();
    }
}
