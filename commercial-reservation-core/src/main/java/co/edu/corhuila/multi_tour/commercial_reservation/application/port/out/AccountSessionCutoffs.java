package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountSessionCutoff;
import java.util.Optional;
import java.util.UUID;

// The cutoff per account (table account_session_cutoff of the db 022). The writes are called inside TenantEventTransaction only.
public interface AccountSessionCutoffs {

    Optional<AccountSessionCutoff> find(UUID accountId);

    // False when a row for the account appeared meanwhile.
    boolean insertIfAbsent(AccountSessionCutoff cutoff);

    // Writes the instant only when it is later than the stored one (plan D-02); false when it is not. A null tenant keeps the tenant stored.
    boolean moveForward(AccountSessionCutoff cutoff);
}
