package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionDeletions;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataDeletion;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-019 plan D-04 (-db 016, 034): the deletion over the real schema. Every statement leads with the tenant. Only the risk rows are deleted (DELETE is granted on the two
// sensitive tables only); the consents and the commercial history are never touched. The record has the reservation as its primary key, so a second record is refused by the
// schema. The lock is a row lock on the customer, held until the end of the transaction of the caller.
public final class JdbcRetentionDeletions implements RetentionDeletions {
    private final JdbcTemplate jdbc;

    public JdbcRetentionDeletions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isDeleted(UUID tenantId, UUID reservationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM commercial_reservation.sensitive_data_deletion WHERE tenant_id = ? AND reservation_id = ?)",
                Boolean.class, tenantId, reservationId));
    }

    @Override
    public List<UUID> otherReservationsOf(UUID tenantId, UUID customerId, UUID reservationId) {
        return jdbc.queryForList("SELECT reservation_id FROM commercial_reservation.reservation WHERE tenant_id = ? AND customer_id = ? AND reservation_id <> ?",
                UUID.class, tenantId, customerId, reservationId);
    }

    @Override
    public void lockCustomer(UUID tenantId, UUID customerId) {
        jdbc.queryForList("SELECT customer_id FROM commercial_reservation.customer WHERE tenant_id = ? AND customer_id = ? FOR UPDATE", UUID.class, tenantId, customerId);
    }

    @Override
    public int deleteCompanionRiskData(UUID tenantId, UUID reservationId) {
        return jdbc.update("DELETE FROM commercial_reservation.reservation_companion_sensitive_data WHERE tenant_id = ? AND reservation_companion_id IN "
                + "(SELECT reservation_companion_id FROM commercial_reservation.reservation_companion WHERE tenant_id = ? AND reservation_id = ?)", tenantId, tenantId, reservationId);
    }

    @Override
    public void deleteCustomerRiskData(UUID tenantId, UUID customerId) {
        jdbc.update("DELETE FROM commercial_reservation.customer_sensitive_data WHERE tenant_id = ? AND customer_id = ?", tenantId, customerId);
    }

    @Override
    public void record(SensitiveDataDeletion deletion) {
        jdbc.update("INSERT INTO commercial_reservation.sensitive_data_deletion (reservation_id, tenant_id, customer_id, anchor_at, anchor_basis, deleted_at, "
                + "customer_data_deleted, companion_rows_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", deletion.reservationId(), deletion.tenantId(), deletion.customerId(),
                deletion.anchorAt().atOffset(ZoneOffset.UTC), deletion.basis().name(), deletion.deletedAt().atOffset(ZoneOffset.UTC), deletion.customerDataDeleted(),
                deletion.companionRowsDeleted());
    }
}
