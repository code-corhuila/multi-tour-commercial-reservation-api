package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaMirrorTransaction;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// The departure row and the processed event id share the connection bound to this transaction (norm 5.3.11).
public final class JdbcSalidaMirrorTransaction implements SalidaMirrorTransaction {
    private final TransactionTemplate transactions;

    public JdbcSalidaMirrorTransaction(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
