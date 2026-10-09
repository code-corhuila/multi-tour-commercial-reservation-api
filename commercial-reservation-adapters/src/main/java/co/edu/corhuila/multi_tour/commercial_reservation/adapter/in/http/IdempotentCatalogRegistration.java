package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import org.springframework.transaction.support.TransactionTemplate;

public final class IdempotentCatalogRegistration {
    public record Result(CatalogService item, boolean created) {}
    private final RegisterCatalogService register;
    private final GetCatalogService get;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentCatalogRegistration(RegisterCatalogService register, GetCatalogService get,
            IdempotencyKeys keys, TransactionTemplate transactions) {
        this.register = register;
        this.get = get;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Result register(RegisterCatalogService.Command command, String key) {
        Result result = transactions.execute(status -> {
            CatalogService item = register.register(command);
            if (keys.tryRegister(command.tenantId(), key, "catalog_service", item.serviceId())) {
                return new Result(item, true);
            }
            status.setRollbackOnly();
            return new Result(item, false);
        });
        if (result.created()) return result;
        // Resolve the original only after rollback; the discarded insert must not survive a retry.
        var id = keys.findResourceId(command.tenantId(), key)
                .orElseThrow(() -> new IllegalStateException("registered key has no resource"));
        return new Result(get.get(command.tenantId(), id), false);
    }
}
