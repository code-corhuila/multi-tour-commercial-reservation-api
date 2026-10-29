package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.function.Supplier;

// What runs inside commits or rolls back as one: the rule and its event (norm 5.3.11).
public interface DiscountRuleTransaction {
    <T> T inTransaction(Supplier<T> work);
}
