package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerAccountLink;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Sets the account of a record that has none, and nothing else of it (AC-08.14). The condition is in the statement, so two
// deliveries racing for one record cannot overwrite each other. It runs inside the transaction of the event id.
public final class JdbcCustomerAccountLink implements CustomerAccountLink {
    private final JdbcTemplate jdbc;

    public JdbcCustomerAccountLink(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean linkIfUnlinked(UUID tenantId, UUID customerId, UUID accountId) {
        return jdbc.update("UPDATE commercial_reservation.customer SET account_id = ? "
                + "WHERE tenant_id = ? AND customer_id = ? AND account_id IS NULL", accountId, tenantId, customerId) == 1;
    }
}
