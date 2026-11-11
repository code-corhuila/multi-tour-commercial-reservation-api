package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// What an approval did: the decided attempt, what was applied to the balance, the excess over it (for the credit balance of
// HU-COM-017) and the value for treatment when the reservation was already cancelled (HU-COM-016). They add up to the verified amount.
public record ProofDecision(PaymentAttempt attempt, Cents applied, Cents excess, Cents treatment) {
}
