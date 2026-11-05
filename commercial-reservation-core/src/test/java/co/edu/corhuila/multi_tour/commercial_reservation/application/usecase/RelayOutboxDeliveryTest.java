package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OutboxDeliveryStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OutboxEventNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeliveryBackoff;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-015 api T-06, RN-03, RN-08: job 7 reads the pending events, marks one published and records a failed attempt. Each attempt counts, keeps a
// truncated reason and computes the next wait (5 s doubling to 15 min); the twentieth marks the event failed and it stays visible. Every value is invented.
class RelayOutboxDeliveryTest {
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private final Map<UUID, OutboxEventView> events = new LinkedHashMap<>();
    private final List<String> writes = new ArrayList<>();
    private final OutboxDeliveryStore store = new OutboxDeliveryStore() {
        public OutboxEventPage pending(Instant now, int page, int limit) {
            List<OutboxEventView> all = events.values().stream().filter(found -> found.status() == OutboxStatus.PENDING
                    && (found.nextAttemptAt() == null || !found.nextAttemptAt().isAfter(now))).toList();
            int from = Math.min((page - 1) * limit, all.size());
            return new OutboxEventPage(all.subList(from, Math.min(from + limit, all.size())), all.size());
        }
        public Optional<OutboxEventView> find(UUID eventId) { return Optional.ofNullable(events.get(eventId)); }
        public boolean markPublished(UUID eventId, Instant now) {
            OutboxEventView found = events.get(eventId);
            if (found == null || found.status() != OutboxStatus.PENDING) return false;
            writes.add("published " + eventId);
            events.put(eventId, view(found, OutboxStatus.PUBLISHED, found.attempts(), found.lastError(), null, now));
            return true;
        }
        public boolean recordAttempt(UUID eventId, int expectedAttempts, String lastError, Instant nextAttemptAt, Instant failedAt) {
            OutboxEventView found = events.get(eventId);
            if (found == null || found.status() != OutboxStatus.PENDING || found.attempts() != expectedAttempts) return false;
            writes.add("attempt " + eventId);
            events.put(eventId, view(found, failedAt == null ? OutboxStatus.PENDING : OutboxStatus.FAILED, expectedAttempts + 1, lastError, nextAttemptAt, null));
            return true;
        }
    };
    private final RelayOutboxDeliveryUseCase relay = new RelayOutboxDeliveryUseCase(store, Clock.fixed(NOW, ZoneOffset.UTC));

    private static OutboxEventView view(OutboxEventView base, OutboxStatus status, int attempts, String error, Instant next, Instant published) {
        return new OutboxEventView(base.eventId(), base.eventType(), base.eventVersion(), base.tenantId(), base.aggregateId(), base.payload(),
                base.correlationId(), base.occurredAt(), published, status, attempts, error, next);
    }

    private UUID pendingNotice() {
        UUID id = UUID.randomUUID();
        events.put(id, new OutboxEventView(id, "ClientNotified", 1, UUID.randomUUID(), UUID.randomUUID(), Map.of("notificationType", "PROOF_APPROVED"),
                "trace-1", NOW.minusSeconds(60), null, OutboxStatus.PENDING, 0, null, null));
        return id;
    }

    @Test
    void theWaitStartsAtFiveSecondsDoublesAndStopsAtFifteenMinutes() {
        assertThat(DeliveryBackoff.waitAfter(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(DeliveryBackoff.waitAfter(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(DeliveryBackoff.waitAfter(3)).isEqualTo(Duration.ofSeconds(20));
        assertThat(DeliveryBackoff.waitAfter(8)).isEqualTo(Duration.ofSeconds(640));
        assertThat(DeliveryBackoff.waitAfter(9)).isEqualTo(Duration.ofMinutes(15));
        assertThat(DeliveryBackoff.waitAfter(19)).isEqualTo(Duration.ofMinutes(15));
        assertThat(DeliveryBackoff.MAX_ATTEMPTS).isEqualTo(20);
    }

    @Test
    void anAttemptRecordsTheErrorAndTheNextWaitAndChangesNothingElse() {
        UUID id = pendingNotice();

        OutboxEventView after = relay.recordAttempt(id, "The mail provider answered 503");

        assertThat(after.attempts()).isEqualTo(1);
        assertThat(after.lastError()).isEqualTo("The mail provider answered 503");
        assertThat(after.nextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(after.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(after.payload()).containsOnlyKeys("notificationType");
        assertThat(writes).containsExactly("attempt " + id);
        assertThat(relay.pending(1, 10).items()).isEmpty();
    }

    @Test
    void theErrorIsTruncatedAndNeverKeepsAnAddress() {
        UUID id = pendingNotice();

        OutboxEventView after = relay.recordAttempt(id, "Rejected for ana.perez@example.com " + "x".repeat(900));

        assertThat(after.lastError()).hasSizeLessThanOrEqualTo(500).doesNotContain("@").doesNotContain("ana.perez");
    }

    @Test
    void theTwentiethAttemptMarksTheEventFailedAndKeepsItVisible() {
        UUID id = pendingNotice();
        OutboxEventView last = null;
        for (int attempt = 1; attempt <= 20; attempt++) {
            last = relay.recordAttempt(id, "The mail provider answered 503");
        }

        assertThat(last.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(last.attempts()).isEqualTo(20);
        assertThat(last.nextAttemptAt()).isNull();
        assertThat(events.get(id)).isNotNull();
        assertThat(relay.pending(1, 10).items()).isEmpty();
        assertThat(relay.recordAttempt(id, "late").attempts()).isEqualTo(20);
        assertThat(writes).hasSize(20);
    }

    @Test
    void markingPublishedTwiceAnswersTheSameStateAndAFailedEventIsNeverPublished() {
        UUID id = pendingNotice();

        OutboxEventView first = relay.markPublished(id);
        OutboxEventView second = relay.markPublished(id);

        assertThat(first.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(second.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(writes).containsExactly("published " + id);
        UUID failed = pendingNotice();
        for (int attempt = 1; attempt <= 20; attempt++) relay.recordAttempt(failed, "boom");
        assertThat(relay.markPublished(failed).status()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertThatThrownBy(() -> relay.markPublished(UUID.randomUUID())).isInstanceOf(OutboxEventNotFoundException.class);
        assertThatThrownBy(() -> relay.recordAttempt(UUID.randomUUID(), "boom")).isInstanceOf(OutboxEventNotFoundException.class);
    }

    @Test
    void thePendingListSkipsTheEventsWaitingForTheirNextAttempt() {
        UUID ready = pendingNotice();
        UUID waiting = pendingNotice();
        relay.recordAttempt(waiting, "boom");

        assertThat(relay.pending(1, 10).items()).extracting(OutboxEventView::eventId).containsExactly(ready);
        assertThat(relay.pending(1, 10).total()).isEqualTo(1);
    }
}
