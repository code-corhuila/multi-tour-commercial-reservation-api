package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;

// One applied rule: its place in the chain, the base it used (original value or previous subtotal), what it took off and what is left.
public record BreakdownLine(int order, String ruleName, DiscountBase calculationBase, Cents base, Cents amount, Cents subtotal) {
}
