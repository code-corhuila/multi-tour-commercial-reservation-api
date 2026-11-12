package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;

// HU-COM-019 RN-02, DD-2 (PROPOSED rule, owner Project Lead): how far one financial decision of a cancelled reservation has been settled. settledAt is the instant its
// value was paid out (refund executed), recorded as a credit balance, or the decision was rejected; null while the decision is still open. frozen tells that an open
// decision has its period frozen by the inactivation of the tenant.
public record DecisionSettlement(Instant settledAt, boolean frozen) {
}
