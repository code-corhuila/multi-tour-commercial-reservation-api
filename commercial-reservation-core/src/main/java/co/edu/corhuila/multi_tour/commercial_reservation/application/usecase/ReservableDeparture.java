package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// RN-05 and RN-06 (plan D-05), in this order: it exists for the tenant, belongs to the chosen tour, is scheduled, has not
// started and has the seats. It reads the capacity row and writes nothing: the hold is HU-COM-009's conditional update.
public class ReservableDeparture {

    private final DepartureReader departures;
    private final Clock clock;

    public ReservableDeparture(DepartureReader departures, Clock clock) {
        this.departures = departures;
        this.clock = clock;
    }

    public DepartureState require(UUID tenantId, UUID departureId, UUID serviceId, int people) {
        DepartureState departure = requireOpen(tenantId, departureId, serviceId);
        int available = departure.seats().available();
        if (available < people) {
            throw new BusinessRuleViolation("not enough seats: " + available + " seats available");
        }
        return departure;
    }

    // What a quote needs: the departure exists for the tenant, is of the tour, is scheduled and has not started (no seat count).
    public DepartureState requireOpen(UUID tenantId, UUID departureId, UUID serviceId) {
        DepartureState departure = departures.find(tenantId, departureId)
                .filter(found -> found.tenantId().equals(tenantId))
                .orElseThrow(() -> new DepartureNotFoundException(departureId));
        if (!departure.serviceId().equals(serviceId)) {
            throw new BusinessRuleViolation("the departure is not a departure of the chosen tour");
        }
        if (departure.status() != DepartureStatus.SCHEDULED) {
            throw new BusinessRuleViolation("the departure is not open for reservations");
        }
        if (!Instant.now(clock).isBefore(departure.startsAt())) {
            throw new BusinessRuleViolation("the departure has already started");
        }
        return departure;
    }
}
