package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// HU-COM-017 plan section 2: the available value of the tenant, the customers that still have credit and the refund requests waiting for a decision.
public record CreditSummary(long availableCents, long customersWithCredit, long requestsToDecide) {
}
