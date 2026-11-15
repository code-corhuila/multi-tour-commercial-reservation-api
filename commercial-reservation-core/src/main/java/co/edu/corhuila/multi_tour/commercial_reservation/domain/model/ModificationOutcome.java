package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// What one modification changed (HU-COM-012 plan D-02): the value before and after, the balance left, the excess of the
// validated payments over the new value (handed to the decision, never dropped) and the seats to hold (+) or release (-).
public record ModificationOutcome(Cents oldFinalValue, Cents newFinalValue, Cents oldPendingBalance, Cents newPendingBalance,
        Cents excess, int seatDelta) {
}
