package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// The departure has fewer free seats than the people asked (AC-07.8): a business refusal with its own code, so that a client can tell
// the customer the departure is full. It stays a BusinessRuleViolation for everything that only needs a rule refusal.
public final class SeatsUnavailableException extends BusinessRuleViolation {
    public SeatsUnavailableException(int available) {
        super("not enough seats: " + available + " seats available");
    }
}
