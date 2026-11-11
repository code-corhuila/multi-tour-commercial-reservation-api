package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.function.Supplier;

// One transaction around a write and its idempotency key (norm 5.3.8, 5.3.11): an exception thrown by the work rolls everything back.
public interface RequestTransaction {
    <T> T inTransaction(Supplier<T> work);
}
