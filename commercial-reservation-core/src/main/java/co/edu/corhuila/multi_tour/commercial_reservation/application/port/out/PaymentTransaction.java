package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.function.Supplier;

// The attempt, its proof, the seat hold and the events commit or roll back as one (norm 5.3.11, ADR-004 D4).
public interface PaymentTransaction {
    <T> T inTransaction(Supplier<T> work);
}
