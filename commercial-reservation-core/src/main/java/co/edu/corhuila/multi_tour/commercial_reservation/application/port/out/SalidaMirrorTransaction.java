package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.function.Supplier;

// The row and the processed event id commit or roll back as one (norm 5.3.11).
public interface SalidaMirrorTransaction {
    <T> T inTransaction(Supplier<T> work);
}
