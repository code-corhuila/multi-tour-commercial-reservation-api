package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.function.Supplier;

// The copy and the processed event id commit or roll back as one (plan D-02, norm 5.3.11).
public interface TenantEventTransaction {
    <T> T inTransaction(Supplier<T> work);
}
