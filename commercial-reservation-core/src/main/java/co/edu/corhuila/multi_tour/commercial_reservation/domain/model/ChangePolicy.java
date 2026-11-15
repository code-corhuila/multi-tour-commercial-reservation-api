package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// The terms of a tour for one change type (RN-RES-004): whether the office may ask for it and how long before the departure start.
public record ChangePolicy(boolean allowed, int leadMinutes) {

    public ChangePolicy {
        if (leadMinutes < 0) throw new DomainException("leadMinutes", "leadMinutes cannot be negative");
    }
}
