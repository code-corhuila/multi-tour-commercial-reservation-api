package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RelayOutboxDelivery;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OutboxDeliveryStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OutboxEventNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeliveryBackoff;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

// HU-COM-015 plan D-08, RN-03, RN-08: job 7 reads, marks and records, and this class writes the delivery data of the outbox row and nothing else (the
// reservation, the payments and the capacity are not reachable from here). A published or failed event answers its state and changes nothing, so every
// call can be repeated. The reason of a failure is one truncated line and never keeps an e-mail address; it is the only text job 7 sends.
public class RelayOutboxDeliveryUseCase implements RelayOutboxDelivery {
    static final int MAX_ERROR = 500;
    private static final Pattern ADDRESS = Pattern.compile("[^\\s@<>\"']+@[^\\s@<>\"']+");
    private final OutboxDeliveryStore store;
    private final Clock clock;

    public RelayOutboxDeliveryUseCase(OutboxDeliveryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public OutboxEventPage pending(int page, int limit) {
        return store.pending(Instant.now(clock), page, limit);
    }

    @Override
    public OutboxEventView markPublished(UUID eventId) {
        OutboxEventView found = load(eventId);
        if (found.status() == OutboxStatus.PENDING) store.markPublished(eventId, Instant.now(clock));
        return load(eventId);
    }

    @Override
    public OutboxEventView recordAttempt(UUID eventId, String error) {
        String reason = sanitize(error);
        for (int tries = 0; tries < 3; tries++) {
            OutboxEventView found = load(eventId);
            if (found.status() != OutboxStatus.PENDING) return found;
            Instant now = Instant.now(clock);
            int attempts = found.attempts() + 1;
            boolean exhausted = attempts >= DeliveryBackoff.MAX_ATTEMPTS;
            Instant next = exhausted ? null : now.plus(DeliveryBackoff.waitAfter(attempts));
            if (store.recordAttempt(eventId, found.attempts(), reason, next, exhausted ? now : null)) break;
        }
        return load(eventId);
    }

    private OutboxEventView load(UUID eventId) {
        return store.find(eventId).orElseThrow(() -> new OutboxEventNotFoundException(eventId));
    }

    static String sanitize(String error) {
        String line = error == null ? "" : ADDRESS.matcher(error.strip()).replaceAll("[address]").replaceAll("\\s+", " ");
        return line.length() <= MAX_ERROR ? line : line.substring(0, MAX_ERROR);
    }
}
