package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEventTransaction;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// The store and the processed ids share the connection bound to this transaction, so they commit or roll back as one.
public final class JdbcTenantEventTransaction implements TenantEventTransaction {
    private final TransactionTemplate transactions;

    public JdbcTenantEventTransaction(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
