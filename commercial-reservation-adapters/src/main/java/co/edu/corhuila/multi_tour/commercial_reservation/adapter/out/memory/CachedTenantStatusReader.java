package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Keeps a found status for a few seconds (DP-TSC-09) so a busy tenant is not read on every request. A missing copy and
// a failure are not kept: the first is cheap to read again and the second must never be remembered as an answer.
public final class CachedTenantStatusReader implements TenantStatusReader {

    private record Kept(TenantStatus status, Instant expiresAt) {
    }

    private final TenantStatusReader source;
    private final Clock clock;
    private final Duration lifetime;
    private final ConcurrentHashMap<UUID, Kept> kept = new ConcurrentHashMap<>();

    public CachedTenantStatusReader(TenantStatusReader source, Clock clock, Duration lifetime) {
        this.source = source;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    @Override
    public Optional<TenantStatus> statusOf(UUID tenantId) {
        Instant now = clock.instant();
        Kept hit = kept.get(tenantId);
        if (hit != null && now.isBefore(hit.expiresAt())) {
            return Optional.of(hit.status());
        }
        Optional<TenantStatus> read = source.statusOf(tenantId);
        read.ifPresentOrElse(status -> kept.put(tenantId, new Kept(status, now.plus(lifetime))), () -> kept.remove(tenantId));
        return read;
    }
}
