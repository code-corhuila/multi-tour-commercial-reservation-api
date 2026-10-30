package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// What a cash payment did: the attempt, what was applied to the balance and the excess over it, which goes to the credit
// balance of HU-COM-017. The excess is not stored on the attempt (the db keeps those columns for decided transfers).
public record CashReceipt(PaymentAttempt attempt, Cents applied, Cents excess) {
}
