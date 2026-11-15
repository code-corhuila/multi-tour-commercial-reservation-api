package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaMirrorTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// One fake store for the departures, the processed event ids and the transaction around them.
// The reader here is deliberately dumb (every row of the service): the use cases must still drop what is not offered.
final class DepartureFakes implements DepartureReader, DepartureStateRepository, ProcessedEvents, SalidaMirrorTransaction {

    final Map<UUID, DepartureState> rows = new LinkedHashMap<>();
    final Set<UUID> processed = new HashSet<>();
    final List<String> journal = new ArrayList<>();

    @Override
    public List<DepartureState> findOpen(UUID tenantId, UUID serviceId, Instant now) {
        return rows.values().stream()
                .filter(row -> row.tenantId().equals(tenantId) && row.serviceId().equals(serviceId))
                .sorted(Comparator.comparing(DepartureState::startsAt)).toList();
    }

    @Override
    public Optional<DepartureState> find(UUID tenantId, UUID departureId) {
        return Optional.ofNullable(rows.get(departureId)).filter(row -> row.tenantId().equals(tenantId));
    }

    @Override
    public void save(DepartureState state) {
        journal.add("save " + state.departureId());
        rows.put(state.departureId(), state);
    }

    @Override
    public boolean wasProcessed(UUID eventId) {
        return processed.contains(eventId);
    }

    @Override
    public void record(UUID eventId) {
        journal.add("record " + eventId);
        processed.add(eventId);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        journal.add("begin");
        T result = work.get();
        journal.add("commit");
        return result;
    }

    void plant(DepartureState state) {
        rows.put(state.departureId(), state);
    }
}
