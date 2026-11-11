package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RequestTransaction;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// The write of an idempotent request and its key commit or roll back as one.
public final class SpringRequestTransaction implements RequestTransaction {
    private final TransactionTemplate transactions;

    public SpringRequestTransaction(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
