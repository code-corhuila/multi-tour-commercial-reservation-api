package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;

// A known key answers the original catalog service and writes nothing (norm 5.3.8); the rule is IdempotentWrites'.
public final class IdempotentCatalogRegistration {
    public record Result(CatalogService item, boolean created) {}
    private final RegisterCatalogService register;
    private final GetCatalogService get;
    private final IdempotentWrites writes;

    public IdempotentCatalogRegistration(RegisterCatalogService register, GetCatalogService get, IdempotentWrites writes) {
        this.register = register;
        this.get = get;
        this.writes = writes;
    }

    public Result register(RegisterCatalogService.Command command, String key) {
        var done = writes.run(command.tenantId(), key, "catalog_service", () -> register.register(command), CatalogService::serviceId,
                id -> get.get(command.tenantId(), id));
        return new Result(done.value(), done.created());
    }
}
