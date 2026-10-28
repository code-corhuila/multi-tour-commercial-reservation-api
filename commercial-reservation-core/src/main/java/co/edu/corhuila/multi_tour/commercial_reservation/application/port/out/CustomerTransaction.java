package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.function.Supplier;

// The customer and its event commit or roll back as one (norm 5.3.11).
public interface CustomerTransaction {
    <T> T inTransaction(Supplier<T> work);
}
