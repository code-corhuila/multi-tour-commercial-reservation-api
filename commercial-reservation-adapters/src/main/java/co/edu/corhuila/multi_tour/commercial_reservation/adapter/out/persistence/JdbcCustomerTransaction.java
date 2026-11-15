package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerTransaction;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// The repository and the outbox share the connection bound to this transaction, so they commit or roll back as one.
public final class JdbcCustomerTransaction implements CustomerTransaction {
    private final TransactionTemplate transactions;

    public JdbcCustomerTransaction(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
