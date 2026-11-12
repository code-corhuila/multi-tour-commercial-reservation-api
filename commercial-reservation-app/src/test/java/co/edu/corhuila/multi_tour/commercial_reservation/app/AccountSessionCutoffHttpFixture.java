package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AccountSessionCutoffs;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountSessionCutoff;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapter of the cutoffs is exercised against the real engine in its own test. Imported by TenantStatusHttpFixture, so every HTTP test starts with no cutoff for any account.
@TestConfiguration
class AccountSessionCutoffHttpFixture {
    @Bean @Primary MemoryCutoffs memoryCutoffs() { return new MemoryCutoffs(); }

    static final class MemoryCutoffs implements AccountSessionCutoffs {
        final Map<UUID, AccountSessionCutoff> cutoffs = new HashMap<>();
        RuntimeException failure;

        void clear() { cutoffs.clear(); failure = null; }
        public Optional<AccountSessionCutoff> find(UUID accountId) {
            if (failure != null) throw failure;
            return Optional.ofNullable(cutoffs.get(accountId));
        }
        public boolean insertIfAbsent(AccountSessionCutoff cutoff) { return cutoffs.putIfAbsent(cutoff.accountId(), cutoff) == null; }
        public boolean moveForward(AccountSessionCutoff cutoff) {
            AccountSessionCutoff kept = cutoffs.get(cutoff.accountId());
            if (kept == null || !kept.revokedFrom().isBefore(cutoff.revokedFrom())) return false;
            cutoffs.put(cutoff.accountId(), cutoff);
            return true;
        }
    }
}
