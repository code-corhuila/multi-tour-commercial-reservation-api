package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.MirrorOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SalidaEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.MirrorSalidaState;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaMirrorTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.util.Optional;
import java.util.UUID;

// Mirrors the departure events into the capacity rows (plan D-08, RN-11). One effect per event id, the status only
// moves forward, and the row and the processed id commit together. The consumer route that calls this is not built here.
public final class MirrorSalidaStateUseCase implements MirrorSalidaState {

    private final DepartureStateRepository departures;
    private final ProcessedEvents processed;
    private final SalidaMirrorTransaction transaction;
    private final CatalogServiceRepository catalog;

    public MirrorSalidaStateUseCase(DepartureStateRepository departures, ProcessedEvents processed,
            SalidaMirrorTransaction transaction, CatalogServiceRepository catalog) {
        this.departures = departures;
        this.processed = processed;
        this.transaction = transaction;
        this.catalog = catalog;
    }

    @Override
    public MirrorOutcome apply(SalidaEvent event) {
        return transaction.inTransaction(() -> {
            if (processed.wasProcessed(event.eventId())) {
                return MirrorOutcome.DUPLICATE;
            }
            MirrorOutcome outcome = switch (event) {
                case SalidaEvent.Scheduled scheduled -> schedule(scheduled);
                case SalidaEvent.ExecutionStarted started -> advance(event, DepartureStatus.IN_EXECUTION);
                case SalidaEvent.Finalized finalized -> advance(event, DepartureStatus.FINALIZED);
                case SalidaEvent.Cancelled cancelled -> advance(event, DepartureStatus.CANCELLED);
                case SalidaEvent.VehiclesChanged changed -> changeVehicles(changed);
            };
            // An event with no row yet stays unmarked so its redelivery can still apply it (DP-16, DD-8).
            if (outcome != MirrorOutcome.IGNORED_UNKNOWN) {
                processed.record(event.eventId());
            }
            return outcome;
        });
    }

    private MirrorOutcome schedule(SalidaEvent.Scheduled event) {
        if (departures.find(event.tenantId(), event.departureId()).isPresent()) {
            return MirrorOutcome.IGNORED_OUT_OF_ORDER;
        }
        Optional<CatalogService> tour = tour(event.tenantId(), event.serviceId());
        if (tour.isEmpty()) {
            return MirrorOutcome.IGNORED_UNKNOWN;
        }
        // Mandatory transport: no seats until the first vehicle event (G-VEH, DP-08).
        int initial = tour.get().terms().transportRequired() ? 0 : capacityOf(tour.get());
        departures.save(new DepartureState(event.departureId(), event.tenantId(), event.serviceId(),
                event.startsAt(), DepartureStatus.SCHEDULED, initial, 0));
        return MirrorOutcome.APPLIED;
    }

    private MirrorOutcome advance(SalidaEvent event, DepartureStatus next) {
        Optional<DepartureState> row = departures.find(event.tenantId(), event.departureId());
        if (row.isEmpty()) {
            return MirrorOutcome.IGNORED_UNKNOWN;
        }
        if (!row.get().status().canMoveTo(next)) {
            return MirrorOutcome.IGNORED_OUT_OF_ORDER;
        }
        departures.save(row.get().withStatus(next));
        return MirrorOutcome.APPLIED;
    }

    private MirrorOutcome changeVehicles(SalidaEvent.VehiclesChanged event) {
        Optional<DepartureState> row = departures.find(event.tenantId(), event.departureId());
        Optional<CatalogService> tour = row.flatMap(state -> tour(state.tenantId(), state.serviceId()));
        if (tour.isEmpty()) {
            return MirrorOutcome.IGNORED_UNKNOWN;
        }
        // The lower of the tour capacity and the vehicle total, never below the seats already held (H8).
        int effective = Math.max(Math.min(capacityOf(tour.get()), event.vehicleTotalCapacity()), row.get().heldSeats());
        departures.save(row.get().withEffectiveCapacity(effective));
        return MirrorOutcome.APPLIED;
    }

    private Optional<CatalogService> tour(UUID tenantId, UUID serviceId) {
        return catalog.findByTenantAndId(tenantId, serviceId)
                .filter(service -> service.tenantId().equals(tenantId) && service.serviceType() == CatalogServiceType.TOUR);
    }

    private static int capacityOf(CatalogService tour) {
        Integer capacity = tour.terms().configuredCapacity();
        return capacity == null ? 0 : capacity;
    }
}
