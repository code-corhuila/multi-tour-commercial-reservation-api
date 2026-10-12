package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentRegistration;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcAssociatedEstablishmentKeys implements AssociatedEstablishmentKeys {
    private static final String RESOURCE_TYPE = "AssociatedEstablishment";
    private final JdbcTemplate jdbc;
    private final JdbcIdempotencyKeys keys;
    private final AssociatedEstablishmentRepository repository;
    private final TransactionTemplate transactions;

    public JdbcAssociatedEstablishmentKeys(JdbcTemplate jdbc, JdbcIdempotencyKeys keys,
            AssociatedEstablishmentRepository repository, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.keys = keys;
        this.repository = repository;
        this.transactions = transactions;
    }

    @Override
    public EstablishmentRegistration register(UUID tenantId, String key, Supplier<AssociatedEstablishment> create,
            Function<UUID, AssociatedEstablishment> lookup) {
        return transactions.execute(status -> {
            // A transaction lock makes the check-create-key sequence exclusive for this tenant and key.
            jdbc.query("SELECT pg_advisory_xact_lock(?, ?)", (row, index) -> row.getObject(1),
                    tenantId.hashCode(), key.hashCode());
            var existing = keys.findResourceId(tenantId, key);
            if (existing.isPresent()) {
                return new EstablishmentRegistration(lookup.apply(existing.get()), false);
            }
            var item = create.get();
            repository.save(item);
            if (!keys.tryRegister(tenantId, key, RESOURCE_TYPE, item.associationId())) {
                throw new IllegalStateException("idempotency key changed while locked");
            }
            return new EstablishmentRegistration(item, true);
        });
    }
}
