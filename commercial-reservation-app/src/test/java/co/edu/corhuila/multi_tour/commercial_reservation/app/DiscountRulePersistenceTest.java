package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentDiscountRuleCreation;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CreateDiscountRuleUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetDiscountRuleUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.InactivateDiscountRuleUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleChanged;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DiscountRulePersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00.123456789Z");
    private static final Instant LATER = NOW.plusSeconds(60);
    private final UUID tenant = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final DiscountRuleActor admin = new DiscountRuleActor(tenant, actorId,
            DiscountRuleActor.Role.TENANT_ADMINISTRATOR);
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private JdbcDiscountRules rules;
    private JdbcIdempotencyKeys keys;
    private CreateDiscountRuleUseCase create;

    @BeforeEach
    void connectAsTheApplicationUser() {
        assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        rules = new JdbcDiscountRules(jdbc, transactions);
        keys = new JdbcIdempotencyKeys(jdbc);
        create = new CreateDiscountRuleUseCase(rules, rules, rules, UUID::randomUUID, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("commercial_reservation_app");
    }

    @Test
    void aPercentageRuleForEveryServiceRoundTripsEveryField() {
        DiscountRule saved = create.create(admin, command("Season", 1250, null, ServiceCoverage.all(), null));
        DiscountRule restored = rules.findByTenantAndId(tenant, saved.ruleId()).orElseThrow();

        assertThat(restored.ruleId()).isEqualTo(saved.ruleId());
        assertThat(restored.tenantId()).isEqualTo(tenant);
        assertThat(restored.name()).isEqualTo("Season");
        assertThat(restored.validFrom()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(restored.validTo()).isEqualTo(LocalDate.parse("2026-12-31"));
        assertThat(restored.priority()).isEqualTo(2);
        assertThat(restored.calculationBase()).isEqualTo(DiscountBase.PREVIOUS_SUBTOTAL);
        assertThat(restored.percentageBasisPoints()).isEqualTo(1250);
        assertThat(restored.amountCents()).isNull();
        assertThat(restored.serviceCoverage().isAll()).isTrue();
        assertThat(restored.capCents()).isNull();
        assertThat(restored.status()).isEqualTo(DiscountStatus.ACTIVE);
        assertThat(restored.createdAt()).isEqualTo(micros(NOW));
        assertThat(restored.updatedAt()).isEqualTo(micros(NOW));
        assertThat(count("discount_rule_service", "discount_rule_id", saved.ruleId())).isZero();
    }

    @Test
    void aFixedAmountRuleForSelectedServicesKeepsItsCapAndItsServiceRows() {
        UUID first = catalogService(tenant);
        UUID second = catalogService(tenant);
        DiscountRule saved = create.create(admin,
                command("Pair", null, 50_000L, ServiceCoverage.of(Set.of(first, second)), 80_000L));
        DiscountRule restored = rules.findByTenantAndId(tenant, saved.ruleId()).orElseThrow();

        assertThat(restored.amountCents()).isEqualTo(50_000L);
        assertThat(restored.percentageBasisPoints()).isNull();
        assertThat(restored.capCents()).isEqualTo(80_000L);
        assertThat(restored.serviceCoverage().isAll()).isFalse();
        assertThat(restored.serviceCoverage().serviceIds()).containsExactlyInAnyOrder(first, second);
        assertThat(jdbc.queryForObject("SELECT discount_type FROM commercial_reservation.discount_rule "
                + "WHERE discount_rule_id = ?", String.class, saved.ruleId())).isEqualTo("FIXED_AMOUNT");
        assertThat(count("discount_rule_service", "discount_rule_id", saved.ruleId())).isEqualTo(2);
    }

    @Test
    void everyReadIsScopedToTheTenant() {
        DiscountRule mine = create.create(admin, command("Mine", 500, null, ServiceCoverage.all(), null));
        UUID otherTenant = UUID.randomUUID();
        DiscountRule theirs = create.create(
                new DiscountRuleActor(otherTenant, actorId, DiscountRuleActor.Role.TENANT_ADMINISTRATOR),
                command("Theirs", 700, null, ServiceCoverage.all(), null));

        assertThat(rules.findByTenantAndId(otherTenant, mine.ruleId())).isEmpty();
        assertThat(rules.findByTenant(tenant)).extracting(DiscountRule::ruleId).containsExactly(mine.ruleId());
        assertThat(rules.findByTenant(otherTenant)).extracting(DiscountRule::ruleId).containsExactly(theirs.ruleId());
    }

    @Test
    void selectedServicesOfAnotherTenantAreRefusedAndNothingIsWritten() {
        UUID foreign = catalogService(UUID.randomUUID());
        assertThatThrownBy(() -> create.create(admin,
                command("Leak", 500, null, ServiceCoverage.of(Set.of(foreign)), null)))
                .isInstanceOf(DomainException.class);

        assertThat(count("discount_rule", "tenant_id", tenant)).isZero();
        assertThat(count("outbox_event", "tenant_id", tenant)).isZero();
    }

    @Test
    void creationWritesTheRuleAndItsEventInOneTransaction() {
        DiscountRule saved = create.create(admin, command("Event", 900, null, ServiceCoverage.all(), null));

        var events = jdbc.queryForList("SELECT event_type, discount_rule_id, service_id, association_id, actor_id, "
                + "previous_catalog_status, new_catalog_status, previous_discount_rule_state IS NULL AS no_previous, "
                + "new_discount_rule_state ->> 'ruleId' AS rule_id, new_discount_rule_state ->> 'status' AS state, "
                + "new_discount_rule_state ->> 'percentageBasisPoints' AS basis_points, published_at "
                + "FROM commercial_reservation.outbox_event WHERE tenant_id = ?", tenant);
        assertThat(events).hasSize(1);
        var event = events.get(0);
        assertThat(event.get("event_type")).isEqualTo("DiscountRuleChanged");
        assertThat(event.get("discount_rule_id")).isEqualTo(saved.ruleId());
        assertThat(event.get("service_id")).isNull();
        assertThat(event.get("association_id")).isNull();
        assertThat(event.get("actor_id")).isEqualTo(actorId);
        assertThat(event.get("previous_catalog_status")).isNull();
        assertThat(event.get("new_catalog_status")).isNull();
        assertThat(event.get("no_previous")).isEqualTo(true);
        assertThat(event.get("rule_id")).isEqualTo(saved.ruleId().toString());
        assertThat(event.get("state")).isEqualTo("ACTIVE");
        assertThat(event.get("basis_points")).isEqualTo("900");
        assertThat(event.get("published_at")).isNull();
    }

    @Test
    void aFailureAfterTheSaveRollsBackTheRuleItsServicesAndItsEvent() {
        UUID service = catalogService(tenant);
        assertThatThrownBy(() -> rules.inTransaction(() -> {
            DiscountRule rule = DiscountRule.create(UUID.randomUUID(), tenant, "Doomed", LocalDate.parse("2026-11-01"),
                    LocalDate.parse("2026-12-31"), 1, DiscountBase.ORIGINAL_VALUE, 100, null,
                    ServiceCoverage.of(Set.of(service)), null, NOW);
            rules.save(rule);
            rules.append(new DiscountRuleChanged(tenant, rule.ruleId(), null, DiscountRuleState.of(rule), actorId, NOW));
            jdbc.update("INSERT INTO commercial_reservation.outbox_event (tenant_id, event_type, actor_id, occurred_at) "
                    + "VALUES (?, 'NotAnEvent', ?, now())", tenant, actorId);
            return rule;
        })).isInstanceOf(DataAccessException.class);

        assertThat(count("discount_rule", "tenant_id", tenant)).isZero();
        assertThat(count("discount_rule_service", "tenant_id", tenant)).isZero();
        assertThat(count("outbox_event", "tenant_id", tenant)).isZero();
    }

    @Test
    void inactivationPersistsTheStatusAndWritesAnEventWithBothStates() {
        UUID service = catalogService(tenant);
        DiscountRule saved = create.create(admin,
                command("Retire", null, 1_000L, ServiceCoverage.of(Set.of(service)), null));
        var inactivate = new InactivateDiscountRuleUseCase(rules, rules, rules, Clock.fixed(LATER, ZoneOffset.UTC));

        inactivate.inactivate(admin, saved.ruleId());
        DiscountRule restored = rules.findByTenantAndId(tenant, saved.ruleId()).orElseThrow();

        assertThat(restored.status()).isEqualTo(DiscountStatus.INACTIVE);
        assertThat(restored.createdAt()).isEqualTo(micros(NOW));
        assertThat(restored.updatedAt()).isEqualTo(micros(LATER));
        assertThat(restored.serviceCoverage().serviceIds()).containsExactly(service);
        var states = jdbc.queryForList("SELECT previous_discount_rule_state ->> 'status' AS before_state, "
                + "new_discount_rule_state ->> 'status' AS after_state FROM commercial_reservation.outbox_event "
                + "WHERE tenant_id = ? AND previous_discount_rule_state IS NOT NULL", tenant);
        assertThat(states).hasSize(1);
        assertThat(states.get(0).get("before_state")).isEqualTo("ACTIVE");
        assertThat(states.get(0).get("after_state")).isEqualTo("INACTIVE");

        inactivate.inactivate(admin, saved.ruleId());
        assertThat(count("outbox_event", "tenant_id", tenant)).isEqualTo(2);
    }

    @Test
    void anotherTenantCannotInactivateOrOverwriteARule() {
        DiscountRule saved = create.create(admin, command("Mine", 500, null, ServiceCoverage.all(), null));
        var intruder = new DiscountRuleActor(UUID.randomUUID(), actorId, DiscountRuleActor.Role.TENANT_ADMINISTRATOR);
        var inactivate = new InactivateDiscountRuleUseCase(rules, rules, rules, Clock.fixed(LATER, ZoneOffset.UTC));

        assertThatThrownBy(() -> inactivate.inactivate(intruder, saved.ruleId()))
                .isInstanceOf(DiscountRuleNotFoundException.class);
        DiscountRule forged = DiscountRule.restore(saved.ruleId(), intruder.tenantId(), "Forged", saved.validFrom(),
                saved.validTo(), 1, DiscountBase.ORIGINAL_VALUE, 100, null, ServiceCoverage.all(), null,
                DiscountStatus.ACTIVE, NOW, NOW);
        assertThatThrownBy(() -> rules.save(forged)).isInstanceOf(IllegalStateException.class);
        DiscountRule untouched = rules.findByTenantAndId(tenant, saved.ruleId()).orElseThrow();
        assertThat(untouched.name()).isEqualTo("Mine");
        assertThat(untouched.status()).isEqualTo(DiscountStatus.ACTIVE);
    }

    @Test
    void replayWithTheSameKeyReturnsTheRuleWithoutAnotherRowOrEvent() {
        var creation = new IdempotentDiscountRuleCreation(create, new GetDiscountRuleUseCase(rules), keys, transactions);
        var command = command("Replay", 300, null, ServiceCoverage.all(), null);

        var first = creation.create(admin, command, "discount-key-one");
        var retry = creation.create(admin, command, "discount-key-one");

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(retry.rule().ruleId()).isEqualTo(first.rule().ruleId());
        assertThat(count("discount_rule", "tenant_id", tenant)).isEqualTo(1);
        assertThat(count("outbox_event", "tenant_id", tenant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT resource_type FROM commercial_reservation.idempotency_key "
                + "WHERE tenant_id = ? AND key = ?", String.class, tenant, "discount-key-one"))
                .isEqualTo("discount_rule");
    }

    @Test
    void aFailedCreationLeavesNoRuleEventOrIdempotencyKeyBehind() {
        CreateDiscountRule failing = (actor, command) -> {
            DiscountRule rule = create.create(actor, command);
            throw new IllegalStateException("boom after " + rule.ruleId());
        };
        var creation = new IdempotentDiscountRuleCreation(failing, new GetDiscountRuleUseCase(rules), keys, transactions);

        assertThatThrownBy(() -> creation.create(admin, command("Boom", 300, null, ServiceCoverage.all(), null),
                "discount-key-boom")).isInstanceOf(IllegalStateException.class);

        assertThat(count("discount_rule", "tenant_id", tenant)).isZero();
        assertThat(count("outbox_event", "tenant_id", tenant)).isZero();
        assertThat(count("idempotency_key", "tenant_id", tenant)).isZero();
    }

    private CreateDiscountRule.Command command(String name, Integer basisPoints, Long cents, ServiceCoverage coverage,
            Long cap) {
        return new CreateDiscountRule.Command(name, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-31"), 2,
                DiscountBase.PREVIOUS_SUBTOTAL, basisPoints, cents, coverage, cap);
    }

    private UUID catalogService(UUID owner) {
        var catalog = new JdbcCatalogServiceRepository(jdbc, transactions);
        CatalogService item = CatalogService.register(UUID.randomUUID(), owner, CatalogServiceType.TOUR, "Tour",
                CommercialTerms.of(1_000L, 500L, 10, true, false), NOW);
        catalog.save(item);
        return item.serviceId();
    }

    private int count(String table, String column, UUID value) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation." + table + " WHERE " + column + " = ?",
                Integer.class, value);
    }

    private static Instant micros(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean present(String name) {
        return System.getenv(name) != null && !System.getenv(name).isBlank();
    }
}
