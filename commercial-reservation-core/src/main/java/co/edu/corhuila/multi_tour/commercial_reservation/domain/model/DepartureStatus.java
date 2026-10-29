package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// Forward-only matrix of AC-06.16 (PROPOSED): SCHEDULED, then IN_EXECUTION, then FINALIZED or CANCELLED.
// The English codes are mapped to the dictionary's Spanish values at the adapter (plan D-08).
public enum DepartureStatus {
    SCHEDULED(1), IN_EXECUTION(2), FINALIZED(3), CANCELLED(3);

    private final int rank;

    DepartureStatus(int rank) {
        this.rank = rank;
    }

    // A final status never changes and a repeated one is not a move.
    public boolean canMoveTo(DepartureStatus next) {
        return rank < next.rank;
    }
}
