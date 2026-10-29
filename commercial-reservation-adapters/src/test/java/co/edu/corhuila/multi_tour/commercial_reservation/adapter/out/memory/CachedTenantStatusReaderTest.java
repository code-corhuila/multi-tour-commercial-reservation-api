package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// api spec 021 T-05 (DP-TSC-09): the status is kept 5 seconds, so an inactivation is seen after that and no sooner.
class CachedTenantStatusReaderTest {

    private static final UUID TENANT = new UUID(1, 1);

    private Instant now = Instant.parse("2026-10-29T14:00:00Z");
    private final Clock clock = new Clock() {
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now; }
    };
    private final Map<UUID, TenantStatus> source = new HashMap<>();
    private int reads;
    private boolean failing;
    private final CachedTenantStatusReader reader = new CachedTenantStatusReader(tenant -> {
        reads++;
        if (failing) throw new IllegalStateException("database down");
        return Optional.ofNullable(source.get(tenant));
    }, clock, Duration.ofSeconds(5));

    @Test
    void aFoundStatusIsKeptForFiveSecondsAndThenReadAgain() {
        source.put(TENANT, TenantStatus.ACTIVE);
        assertThat(reader.statusOf(TENANT)).contains(TenantStatus.ACTIVE);
        source.put(TENANT, TenantStatus.INACTIVE);
        now = now.plusSeconds(4);
        assertThat(reader.statusOf(TENANT)).contains(TenantStatus.ACTIVE);
        assertThat(reads).isEqualTo(1);

        now = now.plusSeconds(1);
        assertThat(reader.statusOf(TENANT)).contains(TenantStatus.INACTIVE);
        assertThat(reads).isEqualTo(2);
    }

    @Test
    void aMissingCopyAndAFailureAreNeverKept() {
        assertThat(reader.statusOf(TENANT)).isEmpty();
        source.put(TENANT, TenantStatus.INACTIVE);
        assertThat(reader.statusOf(TENANT)).contains(TenantStatus.INACTIVE);

        now = now.plusSeconds(6);
        failing = true;
        assertThatThrownBy(() -> reader.statusOf(TENANT)).isInstanceOf(IllegalStateException.class);
    }
}
