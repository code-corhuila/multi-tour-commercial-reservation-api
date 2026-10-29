package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// Effective capacity and held seats of a departure; available = max(effective - held, 0) (plan D-07).
public record SeatAvailability(int effectiveCapacity, int heldSeats) {

    public SeatAvailability {
        if (effectiveCapacity < 0 || heldSeats < 0) {
            throw new DomainException("seats", "capacity and held seats cannot be negative");
        }
    }

    public int available() {
        return Math.max(effectiveCapacity - heldSeats, 0);
    }
}
