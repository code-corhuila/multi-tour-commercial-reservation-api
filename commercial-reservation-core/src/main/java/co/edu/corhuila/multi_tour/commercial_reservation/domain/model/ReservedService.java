package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.UUID;

// The tour of a reservation with the rates used at creation (RN-RES-003, DP-01 per person). The transport
// fare per person is null when the tour has none: the transport is a line of the value, never a second service.
public record ReservedService(UUID serviceId, Cents baseRatePerPerson, Cents transportRatePerPerson) {

    public Cents tourLine(int people) {
        return new Cents(Math.multiplyExact(baseRatePerPerson.amount(), (long) people));
    }

    // Charged once, for the total people (RN-TRA-002); null when the tour has no transport.
    public Cents transportLine(int people) {
        return transportRatePerPerson == null ? null
                : new Cents(Math.multiplyExact(transportRatePerPerson.amount(), (long) people));
    }

    public Cents projectedFor(int people) {
        Cents transport = transportLine(people);
        return new Cents(tourLine(people).amount() + (transport == null ? 0 : transport.amount()));
    }
}
