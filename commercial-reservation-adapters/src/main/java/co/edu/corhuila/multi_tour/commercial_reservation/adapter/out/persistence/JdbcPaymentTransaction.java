package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// The attempt, its proof, the seat hold and the events commit or roll back as one. It joins the transaction the idempotency key opened.
public final class JdbcPaymentTransaction implements PaymentTransaction {
    private final TransactionTemplate transactions;

    public JdbcPaymentTransaction(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
