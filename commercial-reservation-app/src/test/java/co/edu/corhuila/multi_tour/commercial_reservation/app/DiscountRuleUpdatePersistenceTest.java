package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CreateDiscountRuleUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.InactivateDiscountRuleUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReactivateDiscountRuleUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.UpdateDiscountRuleUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRulePatch;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-003 AC-03.7 (persisted flag), AC-03.12, AC-03.16, AC-03.17 against the real engine as the application user.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DiscountRuleUpdatePersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");
    private static final Clock LATER = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);
    private final UUID tenant = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final DiscountRuleActor admin = new DiscountRuleActor(tenant, actorId, DiscountRuleActor.Role.TENANT_ADMINISTRATOR);
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private JdbcDiscountRules rules;
    private CreateDiscountRuleUseCase create;
    private UpdateDiscountRuleUseCase update;
    private ReactivateDiscountRuleUseCase reactivate;
    private InactivateDiscountRuleUseCase inactivate;

    @BeforeEach
    void connectAsTheApplicationUser() {
        assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        rules = new JdbcDiscountRules(jdbc, transactions);
        create = new CreateDiscountRuleUseCase(rules, rules, rules, UUID::randomUUID, Clock.fixed(NOW, ZoneOffset.UTC));
        update = new UpdateDiscountRuleUseCase(rules, rules, rules, LATER);
        reactivate = new ReactivateDiscountRuleUseCase(rules, rules, rules, LATER);
        inactivate = new InactivateDiscountRuleUseCase(rules, rules, rules, LATER);
    }

    @Test
    void theFlagIsStoredAndReadAndARuleCreatedWithoutItReadsTrue() {
        DiscountRule stops = create.create(admin, command("Stops", ServiceCoverage.all(), false));
        DiscountRule plain = create.create(admin, command("Plain", ServiceCoverage.all(), null));
        assertThat(rules.findByTenantAndId(tenant, stops.ruleId()).orElseThrow().stackable()).isFalse();
        assertThat(rules.findByTenantAndId(tenant, plain.ruleId()).orElseThrow().stackable()).isTrue();
        assertThat(jdbc.queryForObject("SELECT stackable FROM commercial_reservation.discount_rule WHERE discount_rule_id = ?",
                Boolean.class, stops.ruleId())).isFalse();
    }

    @Test
    void AC_03_16_anUpdatePersistsEveryEditableFieldAndWritesOneEventWithBothStates() {
        DiscountRule rule = create.create(admin, command("Season", ServiceCoverage.all(), null));
        update.update(admin, rule.ruleId(), DiscountRulePatch.none().withName("High").withPriority(5)
                .withValidFrom(LocalDate.parse("2026-11-05")).withValidTo(LocalDate.parse("2026-12-20"))
                .withCalculationBase(DiscountBase.ORIGINAL_VALUE).withAmountCents(70_000L).withCapCents(90_000L)
                .withStackable(false));

        DiscountRule restored = rules.findByTenantAndId(tenant, rule.ruleId()).orElseThrow();
        assertThat(restored.name()).isEqualTo("High");
        assertThat(restored.priority()).isEqualTo(5);
        assertThat(restored.validFrom()).isEqualTo(LocalDate.parse("2026-11-05"));
        assertThat(restored.validTo()).isEqualTo(LocalDate.parse("2026-12-20"));
        assertThat(restored.calculationBase()).isEqualTo(DiscountBase.ORIGINAL_VALUE);
        assertThat(restored.percentageBasisPoints()).isNull();
        assertThat(restored.amountCents()).isEqualTo(70_000L);
        assertThat(restored.capCents()).isEqualTo(90_000L);
        assertThat(restored.stackable()).isFalse();
        assertThat(restored.createdAt()).isEqualTo(NOW);
        assertThat(restored.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(jdbc.queryForObject("SELECT discount_type FROM commercial_reservation.discount_rule WHERE discount_rule_id = ?",
                String.class, rule.ruleId())).isEqualTo("FIXED_AMOUNT");

        List<Map<String, Object>> events = events(rule);
        assertThat(events).hasSize(2);
        String previous = events.get(1).get("previous_discount_rule_state").toString();
        String current = events.get(1).get("new_discount_rule_state").toString();
        assertThat(previous).contains("\"name\": \"Season\"").contains("\"stackable\": true");
        assertThat(current).contains("\"name\": \"High\"").contains("\"stackable\": false").contains("\"amountCents\": 70000");
    }

    @Test
    void AC_03_16_aBodyThatChangesNothingWritesNothing() {
        DiscountRule rule = create.create(admin, command("Season", ServiceCoverage.all(), null));
        update.update(admin, rule.ruleId(), DiscountRulePatch.none().withName("Season"));
        assertThat(events(rule)).hasSize(1);
        assertThat(rules.findByTenantAndId(tenant, rule.ruleId()).orElseThrow().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void AC_03_16_theCoverageCanMoveToAnotherTourAndGrow() {
        UUID first = catalogService(), second = catalogService(), third = catalogService();
        DiscountRule rule = create.create(admin, command("One tour", ServiceCoverage.of(Set.of(first)), null));
        update.update(admin, rule.ruleId(), DiscountRulePatch.none().withServiceCoverage(ServiceCoverage.of(Set.of(second))));
        assertThat(coverageOf(rule)).containsExactly(second);
        update.update(admin, rule.ruleId(), DiscountRulePatch.none().withServiceCoverage(ServiceCoverage.of(Set.of(second, third))));
        assertThat(coverageOf(rule)).containsExactlyInAnyOrder(second, third);
    }

    @Test
    void AC_03_16_aRuleCanBecomeEveryServiceAndComeBackToOneTour() {
        UUID first = catalogService(), second = catalogService();
        DiscountRule rule = create.create(admin, command("One tour", ServiceCoverage.of(Set.of(first)), null));
        update.update(admin, rule.ruleId(), DiscountRulePatch.none().withServiceCoverage(ServiceCoverage.all()));
        assertThat(rules.findByTenantAndId(tenant, rule.ruleId()).orElseThrow().serviceCoverage().isAll()).isTrue();
        update.update(admin, rule.ruleId(), DiscountRulePatch.none().withServiceCoverage(ServiceCoverage.of(Set.of(second))));
        assertThat(coverageOf(rule)).containsExactly(second);
    }

    @Test
    void AC_03_16_aSmallerSelectionIsRefusedBecauseNoRowOfTheCoverageCanBeDeleted() {
        UUID first = catalogService(), second = catalogService();
        DiscountRule rule = create.create(admin, command("Pair", ServiceCoverage.of(Set.of(first, second)), null));
        assertThatThrownBy(() -> update.update(admin, rule.ruleId(),
                DiscountRulePatch.none().withName("Renamed").withServiceCoverage(ServiceCoverage.of(Set.of(first)))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("serviceCoverage"));
        assertThat(coverageOf(rule)).containsExactlyInAnyOrder(first, second);
        assertThat(rules.findByTenantAndId(tenant, rule.ruleId()).orElseThrow().name()).isEqualTo("Pair");
        assertThat(events(rule)).hasSize(1);
    }

    @Test
    void AC_03_16_aServiceOfAnotherTenantIsRefused() {
        DiscountRule rule = create.create(admin, command("Season", ServiceCoverage.all(), null));
        UUID foreign = foreignCatalogService();
        assertThatThrownBy(() -> update.update(admin, rule.ruleId(),
                DiscountRulePatch.none().withServiceCoverage(ServiceCoverage.of(Set.of(foreign)))))
                .isInstanceOf(DomainException.class);
        assertThat(events(rule)).hasSize(1);
    }

    @Test
    void AC_03_17_reactivationPersistsTheStatusAndWritesOneEvent() {
        DiscountRule rule = create.create(admin, command("Season", ServiceCoverage.all(), null));
        inactivate.inactivate(admin, rule.ruleId());
        new ReactivateDiscountRuleUseCase(rules, rules, rules, Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC)).reactivate(admin, rule.ruleId()); // a later moment keeps the event order deterministic
        assertThat(rules.findByTenantAndId(tenant, rule.ruleId()).orElseThrow().status()).isEqualTo(DiscountStatus.ACTIVE);
        List<Map<String, Object>> events = events(rule);
        assertThat(events).hasSize(3);
        assertThat(events.get(2).get("previous_discount_rule_state").toString()).contains("\"status\": \"INACTIVE\"");
        assertThat(events.get(2).get("new_discount_rule_state").toString()).contains("\"status\": \"ACTIVE\"");
        reactivate.reactivate(admin, rule.ruleId());
        assertThat(events(rule)).hasSize(3);
    }

    @Test
    void AC_03_13_aRuleOfAnotherTenantCannotBeUpdatedOrReactivated() {
        DiscountRule rule = create.create(admin, command("Season", ServiceCoverage.all(), null));
        var stranger = new DiscountRuleActor(UUID.randomUUID(), actorId, DiscountRuleActor.Role.TENANT_ADMINISTRATOR);
        assertThatThrownBy(() -> update.update(stranger, rule.ruleId(), DiscountRulePatch.none().withName("x")))
                .isInstanceOf(DiscountRuleNotFoundException.class);
        assertThatThrownBy(() -> reactivate.reactivate(stranger, rule.ruleId()))
                .isInstanceOf(DiscountRuleNotFoundException.class);
        assertThat(rules.findByTenantAndId(tenant, rule.ruleId()).orElseThrow().name()).isEqualTo("Season");
    }

    @Test
    void anUpdateWhoseEventCannotBeWrittenLeavesTheRuleAsItWas() {
        DiscountRule rule = create.create(admin, command("Season", ServiceCoverage.all(), null));
        var failing = new UpdateDiscountRuleUseCase(rules, event -> { throw new IllegalStateException("outbox down"); }, rules, LATER);
        assertThatThrownBy(() -> failing.update(admin, rule.ruleId(), DiscountRulePatch.none().withName("Lost")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(rules.findByTenantAndId(tenant, rule.ruleId()).orElseThrow().name()).isEqualTo("Season");
        assertThat(events(rule)).hasSize(1);
    }

    private List<Map<String, Object>> events(DiscountRule rule) {
        return jdbc.queryForList("SELECT * FROM commercial_reservation.outbox_event WHERE discount_rule_id = ? ORDER BY occurred_at, outbox_event_id",
                rule.ruleId());
    }

    private Set<UUID> coverageOf(DiscountRule rule) {
        return rules.findByTenantAndId(tenant, rule.ruleId()).orElseThrow().serviceCoverage().serviceIds();
    }

    private CreateDiscountRule.Command command(String name, ServiceCoverage coverage, Boolean stackable) {
        return new CreateDiscountRule.Command(name, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-31"), 2,
                DiscountBase.PREVIOUS_SUBTOTAL, 1250, null, coverage, null, stackable);
    }

    private UUID catalogService() { return catalogServiceOf(tenant); }
    private UUID foreignCatalogService() { return catalogServiceOf(UUID.randomUUID()); }

    private UUID catalogServiceOf(UUID owner) {
        var catalog = new JdbcCatalogServiceRepository(jdbc, transactions);
        CatalogService item = CatalogService.register(UUID.randomUUID(), owner, CatalogServiceType.TOUR, "Tour",
                CommercialTerms.of(1_000L, 500L, 10, true, false), NOW);
        catalog.save(item);
        return item.serviceId();
    }

    private static boolean present(String name) {
        return System.getenv(name) != null && !System.getenv(name).isBlank();
    }
}
