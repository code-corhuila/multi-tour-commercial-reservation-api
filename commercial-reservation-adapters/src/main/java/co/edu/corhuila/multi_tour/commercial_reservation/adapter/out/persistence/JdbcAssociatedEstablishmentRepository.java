package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentChangePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EstablishmentChangeLog;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentAssociationEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentContact;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentChange;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentAssociationInactivated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentAssociationPublished;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentAssociationReactivated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcAssociatedEstablishmentRepository implements AssociatedEstablishmentRepository, EstablishmentChangeLog {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcAssociatedEstablishmentRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public void save(AssociatedEstablishment item) {
        transactions.executeWithoutResult(status -> {
            int changed = jdbc.update("UPDATE commercial_reservation.associated_establishment SET "
                    + "establishment_type = ?, commercial_name = ?, commercial_information = ?, "
                    + "association_status = ?, updated_at = ?, contact_email = ?, contact_phone = ?, contact_address = ? "
                    + "WHERE tenant_id = ? AND association_id = ?",
                    item.establishmentType().name(), item.commercialName(), item.commercialInformation(),
                    item.associationStatus().name(), timestamp(item.updatedAt()), item.contact().email(), item.contact().phone(),
                    item.contact().address(), item.tenantId(), item.associationId());
            if (changed == 0) {
                boolean anotherTenantOwnsId = !jdbc.query("SELECT 1 FROM commercial_reservation.associated_establishment "
                        + "WHERE association_id = ?", (row, index) -> row.getInt(1), item.associationId()).isEmpty();
                if (anotherTenantOwnsId) throw new IllegalStateException("association belongs to another tenant");
                jdbc.update("INSERT INTO commercial_reservation.associated_establishment "
                        + "(association_id, tenant_id, establishment_type, commercial_name, commercial_information, "
                        + "association_status, created_at, updated_at, contact_email, contact_phone, contact_address) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        item.associationId(), item.tenantId(), item.establishmentType().name(), item.commercialName(),
                        item.commercialInformation(), item.associationStatus().name(), timestamp(item.createdAt()),
                        timestamp(item.updatedAt()), item.contact().email(), item.contact().phone(), item.contact().address());
            }
            for (var event : item.pullEvents()) insertEvent(event);
            for (var change : item.pullChanges()) insertChange(change);
        });
    }

    @Override
    public Optional<AssociatedEstablishment> findByTenantAndId(UUID tenantId, UUID associationId) {
        return jdbc.query("SELECT a.association_id, a.tenant_id, a.establishment_type, a.commercial_name, "
                        + "a.commercial_information, a.association_status, a.created_at, a.updated_at, i.image_id, "
                        + "a.contact_email, a.contact_phone, a.contact_address "
                        + "FROM commercial_reservation.associated_establishment a LEFT JOIN "
                        + "commercial_reservation.associated_establishment_image i ON i.tenant_id = a.tenant_id "
                        + "AND i.association_id = a.association_id WHERE a.tenant_id = ? AND a.association_id = ?",
                (row, index) -> map(row), tenantId, associationId).stream().findFirst();
    }

    @Override
    public EstablishmentPage listByTenant(UUID tenantId, Optional<AssociationStatus> status,
            Optional<EstablishmentType> type, int page, int limit) {
        var predicates = new ArrayList<String>();
        var arguments = new ArrayList<Object>();
        predicates.add("tenant_id = ?");
        arguments.add(tenantId);
        status.ifPresent(value -> { predicates.add("association_status = ?"); arguments.add(value.name()); });
        type.ifPresent(value -> { predicates.add("establishment_type = ?"); arguments.add(value.name()); });
        String where = String.join(" AND ", predicates);
        long total = jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.associated_establishment WHERE "
                + where, Long.class, arguments.toArray());
        arguments.add(limit);
        arguments.add((page - 1L) * limit);
        List<AssociatedEstablishment> items = jdbc.query("SELECT a.association_id, a.tenant_id, "
                + "a.establishment_type, a.commercial_name, a.commercial_information, a.association_status, "
                + "a.created_at, a.updated_at, i.image_id, a.contact_email, a.contact_phone, a.contact_address "
                + "FROM commercial_reservation.associated_establishment a "
                + "LEFT JOIN commercial_reservation.associated_establishment_image i "
                + "ON i.tenant_id = a.tenant_id AND i.association_id = a.association_id WHERE " + qualify(where)
                + " ORDER BY a.created_at DESC, a.association_id DESC LIMIT ? OFFSET ?",
                (row, index) -> map(row), arguments.toArray());
        return new EstablishmentPage(items, total);
    }

    // The trace is appended in the transaction of the state it describes and is never edited (HU-COM-002, AC-02.14).
    private void insertChange(EstablishmentChange change) {
        jdbc.update("INSERT INTO commercial_reservation.associated_establishment_change "
                + "(change_id, tenant_id, association_id, event_type, reason, actor_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                change.changeId(), change.tenantId(), change.associationId(), change.eventType().name(), change.reason(),
                change.actorId(), timestamp(change.occurredAt()));
    }

    @Override
    public EstablishmentChangePage listByTenant(UUID tenantId, Optional<EstablishmentType> type, int page, int limit) {
        String join = "FROM commercial_reservation.associated_establishment_change c JOIN commercial_reservation.associated_establishment a "
                + "ON a.tenant_id = c.tenant_id AND a.association_id = c.association_id WHERE c.tenant_id = ?"
                + (type.isPresent() ? " AND a.establishment_type = ?" : "");
        var arguments = new ArrayList<Object>(List.of(tenantId));
        type.ifPresent(value -> arguments.add(value.name()));
        long total = jdbc.queryForObject("SELECT count(*) " + join, Long.class, arguments.toArray());
        arguments.add(limit);
        arguments.add((page - 1L) * limit);
        List<EstablishmentChange> items = jdbc.query("SELECT c.change_id, c.tenant_id, c.association_id, a.commercial_name, c.event_type, "
                + "c.reason, c.actor_id, c.occurred_at " + join + " ORDER BY c.occurred_at DESC, c.change_id DESC LIMIT ? OFFSET ?",
                (row, index) -> new EstablishmentChange(row.getObject("change_id", UUID.class), row.getObject("tenant_id", UUID.class),
                        row.getObject("association_id", UUID.class), row.getString("commercial_name"),
                        EstablishmentChangeType.valueOf(row.getString("event_type")), row.getString("reason"),
                        row.getObject("actor_id", UUID.class), instant(row, "occurred_at")), arguments.toArray());
        return new EstablishmentChangePage(items, total);
    }

    private void insertEvent(EstablishmentAssociationEvent event) {
        String type;
        String next;
        UUID tenant;
        UUID association;
        UUID actor;
        Instant occurredAt;
        if (event instanceof EstablishmentAssociationPublished published) {
            type = "EstablishmentAssociationPublished"; next = "ACTIVE";
            tenant = published.tenantId(); association = published.associationId();
            actor = published.actorId(); occurredAt = published.occurredAt();
        } else if (event instanceof EstablishmentAssociationInactivated inactivated) {
            type = "EstablishmentAssociationInactivated"; next = "INACTIVE";
            tenant = inactivated.tenantId(); association = inactivated.associationId();
            actor = inactivated.actorId(); occurredAt = inactivated.occurredAt();
        } else if (event instanceof EstablishmentAssociationReactivated reactivated) {
            type = "EstablishmentAssociationReactivated"; next = "ACTIVE";
            tenant = reactivated.tenantId(); association = reactivated.associationId();
            actor = reactivated.actorId(); occurredAt = reactivated.occurredAt();
        } else {
            throw new IllegalArgumentException("unsupported establishment event");
        }
        jdbc.update("INSERT INTO commercial_reservation.outbox_event "
                + "(tenant_id, event_type, association_id, service_id, previous_catalog_status, "
                + "new_catalog_status, actor_id, occurred_at) VALUES (?, ?, ?, NULL, NULL, ?, ?, ?)",
                tenant, type, association, next, actor, timestamp(occurredAt));
    }

    private static AssociatedEstablishment map(ResultSet row) throws SQLException {
        return AssociatedEstablishment.restore(row.getObject("association_id", UUID.class),
                row.getObject("tenant_id", UUID.class), EstablishmentType.valueOf(row.getString("establishment_type")),
                row.getString("commercial_name"), row.getString("commercial_information"),
                row.getObject("image_id", UUID.class) == null ? null : row.getObject("image_id", UUID.class).toString(),
                AssociationStatus.valueOf(row.getString("association_status")), instant(row, "created_at"),
                instant(row, "updated_at"), new EstablishmentContact(row.getString("contact_email"),
                        row.getString("contact_phone"), row.getString("contact_address")));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime timestamp(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private static String qualify(String where) {
        return where.replace("tenant_id", "a.tenant_id").replace("association_status", "a.association_status")
                .replace("establishment_type", "a.establishment_type");
    }
}
