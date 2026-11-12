package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AccountSessionCutoffs;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountSessionCutoff;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The cutoff per account over account_session_cutoff (db spec 022). One read by primary key per authenticated request; the writes are used inside the transaction of the use case.
public final class JdbcAccountSessionCutoffs implements AccountSessionCutoffs {
    private final JdbcTemplate jdbc;

    public JdbcAccountSessionCutoffs(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AccountSessionCutoff> find(UUID accountId) {
        return jdbc.query("SELECT tenant_id, revoked_from FROM commercial_reservation.account_session_cutoff WHERE account_id = ?",
                (row, index) -> new AccountSessionCutoff(accountId, row.getObject("tenant_id", UUID.class),
                        row.getTimestamp("revoked_from").toInstant()), accountId).stream().findFirst();
    }

    @Override
    public boolean insertIfAbsent(AccountSessionCutoff cutoff) {
        Timestamp now = Timestamp.from(Instant.now());
        return jdbc.update("INSERT INTO commercial_reservation.account_session_cutoff (account_id, tenant_id, revoked_from, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (account_id) DO NOTHING", cutoff.accountId(), cutoff.tenantId(),
                Timestamp.from(cutoff.revokedFrom()), now, now) == 1;
    }

    @Override
    public boolean moveForward(AccountSessionCutoff cutoff) {
        return jdbc.update("UPDATE commercial_reservation.account_session_cutoff SET revoked_from = ?, tenant_id = COALESCE(?, tenant_id), updated_at = ? "
                + "WHERE account_id = ? AND revoked_from < ?", Timestamp.from(cutoff.revokedFrom()), cutoff.tenantId(),
                Timestamp.from(Instant.now()), cutoff.accountId(), Timestamp.from(cutoff.revokedFrom())) == 1;
    }
}
