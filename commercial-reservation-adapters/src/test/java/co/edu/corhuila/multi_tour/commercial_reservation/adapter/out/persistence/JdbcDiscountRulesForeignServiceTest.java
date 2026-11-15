package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

// A service of another tenant is a request error naming serviceIds (HU-COM-003), never an internal error.
class JdbcDiscountRulesForeignServiceTest {
    private final List<String> writes = new ArrayList<>();

    // The catalog holds no service of this tenant, so every selected service is "foreign".
    private final JdbcTemplate jdbc = new JdbcTemplate() {
        @Override public int update(String sql, Object... args) { writes.add(sql); return 0; }
        @Override public List<java.util.Map<String, Object>> queryForList(String sql, Object... args) { return List.of(); }
        @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) { return List.of(); }
    };

    private final PlatformTransactionManager manager = new PlatformTransactionManager() {
        @Override public org.springframework.transaction.TransactionStatus getTransaction(TransactionDefinition d) {
            return new SimpleTransactionStatus();
        }
        @Override public void commit(org.springframework.transaction.TransactionStatus status) { }
        @Override public void rollback(org.springframework.transaction.TransactionStatus status) { }
    };

    @Test
    void aSelectedServiceOfAnotherTenantIsRefusedNamingServiceIdsAndNothingIsInserted() {
        var rules = new JdbcDiscountRules(jdbc, new TransactionTemplate(manager));
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        DiscountRule rule = DiscountRule.restore(UUID.randomUUID(), UUID.randomUUID(), "Leak",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 1, DiscountBase.ORIGINAL_VALUE, 500, null,
                ServiceCoverage.of(Set.of(UUID.randomUUID())), null, DiscountStatus.ACTIVE, now, now);

        Throwable refused = catchThrowable(() -> rules.save(rule));

        assertThat(refused).isInstanceOf(DomainException.class);
        assertThat(((DomainException) refused).field()).isEqualTo("serviceIds");
        assertThat(writes).noneMatch(sql -> sql.startsWith("INSERT"));
    }
}
