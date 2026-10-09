package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogPage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcCatalogServiceRepository implements CatalogServiceRepository {
    private static final String COLUMNS = "service_id, tenant_id, service_type, commercial_name, catalog_status, "
            + "base_rate_cents, base_operational_cost_cents, configured_capacity, transport_required, high_risk, "
            + "created_at, updated_at";
    private static final String SELECT = "SELECT " + COLUMNS + " FROM commercial_reservation.catalog_service";
    private static final String UPSERT = "INSERT INTO commercial_reservation.catalog_service (" + COLUMNS + ") "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (service_id) DO UPDATE SET "
            + "service_type = EXCLUDED.service_type, commercial_name = EXCLUDED.commercial_name, "
            + "catalog_status = EXCLUDED.catalog_status, base_rate_cents = EXCLUDED.base_rate_cents, "
            + "base_operational_cost_cents = EXCLUDED.base_operational_cost_cents, "
            + "configured_capacity = EXCLUDED.configured_capacity, transport_required = EXCLUDED.transport_required, "
            + "high_risk = EXCLUDED.high_risk, updated_at = EXCLUDED.updated_at "
            + "WHERE catalog_service.tenant_id = EXCLUDED.tenant_id";
    private static final String OUTBOX = "INSERT INTO commercial_reservation.outbox_event "
            + "(tenant_id, event_type, service_id, previous_catalog_status, new_catalog_status, actor_id, "
            + "occurred_at, published_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL)";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcCatalogServiceRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactions.getTransactionManager(), transactions);
        // A caller's resource-and-key transaction must also own the item and its events.
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public void save(CatalogService service) {
        transactions.executeWithoutResult(status -> {
            CommercialTerms terms = service.terms();
            int affected = jdbc.update(UPSERT, service.serviceId(), service.tenantId(), service.serviceType().name(),
                    service.commercialName(), service.catalogStatus().name(), terms.baseRate().amount(),
                    terms.baseOperationalCost().amount(), terms.configuredCapacity(), terms.transportRequired(),
                    terms.highRisk(), timestamp(service.createdAt()), timestamp(service.updatedAt()));
            if (affected == 0) {
                throw new IllegalStateException("Catalog service belongs to another tenant");
            }
            for (CatalogServiceEvent event : service.pullEvents()) {
                jdbc.update(OUTBOX, event.tenantId(), event.getClass().getSimpleName(), event.serviceId(),
                        event.previousStatus().name(), event.newStatus().name(), event.actorId(),
                        timestamp(event.occurredAt()));
            }
        });
    }

    @Override
    public Optional<CatalogService> findByTenantAndId(UUID tenantId, UUID serviceId) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? AND service_id = ?", this::restore, tenantId, serviceId)
                .stream().findFirst();
    }

    @Override
    public List<CatalogService> listByTenant(UUID tenantId, Optional<CatalogStatus> status) {
        String order = " ORDER BY created_at DESC, service_id DESC";
        return status.isPresent()
                ? jdbc.query(SELECT + " WHERE tenant_id = ? AND catalog_status = ?" + order, this::restore,
                        tenantId, status.get().name())
                : jdbc.query(SELECT + " WHERE tenant_id = ?" + order, this::restore, tenantId);
    }

    @Override
    public CatalogPage pageByTenant(UUID tenantId, Optional<CatalogStatus> status,
            Optional<CatalogServiceType> type, int page, int limit) {
        String where = " WHERE tenant_id = ?";
        List<Object> parameters = new ArrayList<>(List.of(tenantId));
        if (status.isPresent()) {
            where += " AND catalog_status = ?";
            parameters.add(status.get().name());
        }
        if (type.isPresent()) {
            where += " AND service_type = ?";
            parameters.add(type.get().name());
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.catalog_service" + where,
                Long.class, parameters.toArray());
        parameters.add(limit);
        parameters.add((page - 1L) * limit);
        var items = jdbc.query(SELECT + where + " ORDER BY created_at DESC, service_id DESC LIMIT ? OFFSET ?",
                this::restore, parameters.toArray());
        return new CatalogPage(items, total);
    }

    private CatalogService restore(ResultSet row, int index) throws SQLException {
        CommercialTerms terms = CommercialTerms.of(row.getLong("base_rate_cents"),
                row.getLong("base_operational_cost_cents"), row.getObject("configured_capacity", Integer.class),
                row.getBoolean("transport_required"), row.getBoolean("high_risk"));
        return CatalogService.restore(row.getObject("service_id", UUID.class), row.getObject("tenant_id", UUID.class),
                CatalogServiceType.valueOf(row.getString("service_type")), row.getString("commercial_name"),
                CatalogStatus.valueOf(row.getString("catalog_status")), terms,
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime timestamp(Instant value) {
        // PostgreSQL rounds excess precision; truncate first to keep the specified microsecond boundary.
        return value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
