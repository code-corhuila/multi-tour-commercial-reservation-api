package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import java.util.UUID;

// One applied rule: its place in the chain, the rule it came from (DD-8 of HU-COM-007: a reservation fixes the line
// without matching by name), the base it used (original value or previous subtotal), what it took off and what is left.
public record BreakdownLine(int order, UUID ruleId, String ruleName, DiscountBase calculationBase, Cents base, Cents amount, Cents subtotal) {
}
