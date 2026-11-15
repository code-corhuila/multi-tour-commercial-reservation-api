package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleChanged;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// One class for the three ports of the slice, as the in-memory stand-in: the rule, its event and the transaction
// that holds both must share one connection to commit or roll back together (norm 5.3.11).
public final class JdbcDiscountRules implements DiscountRuleRepository, DiscountRuleChangeOutbox, DiscountRuleTransaction {
    private static final String COLUMNS = "discount_rule_id, tenant_id, name, starts_on, ends_on, priority, "
            + "calculation_base, discount_type, discount_percentage_bp, discount_amount_cents, status, "
            + "covers_all_services, cap_cents, stackable, created_at, updated_at";
    private static final String SELECT = "SELECT " + COLUMNS + " FROM commercial_reservation.discount_rule";
    private static final String OUTBOX = "INSERT INTO commercial_reservation.outbox_event (tenant_id, event_type, "
            + "discount_rule_id, previous_discount_rule_state, new_discount_rule_state, actor_id, occurred_at) "
            + "VALUES (?, 'DiscountRuleChanged', ?, ?::jsonb, ?::jsonb, ?, ?)";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcDiscountRules(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactions.getTransactionManager(), transactions);
        // The caller's transaction (use case or idempotent creation) must own the rule, its services and its event.
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public void save(DiscountRule rule) {
        transactions.executeWithoutResult(status -> {
            // An update (AC-03.16), an inactivation or a reactivation writes the whole editable state of the rule.
            int changed = jdbc.update("UPDATE commercial_reservation.discount_rule SET name = ?, starts_on = ?, ends_on = ?, "
                    + "priority = ?, calculation_base = ?, discount_type = ?, discount_percentage_bp = ?, "
                    + "discount_amount_cents = ?, status = ?, covers_all_services = ?, cap_cents = ?, stackable = ?, "
                    + "updated_at = ? WHERE tenant_id = ? AND discount_rule_id = ?", rule.name(), rule.validFrom(),
                    rule.validTo(), rule.priority(), rule.calculationBase().name(),
                    rule.percentageBasisPoints() != null ? "PERCENTAGE" : "FIXED_AMOUNT", rule.percentageBasisPoints(),
                    rule.amountCents(), rule.status().name(), rule.serviceCoverage().isAll(), rule.capCents(),
                    rule.stackable(), timestamp(rule.updatedAt()), rule.tenantId(), rule.ruleId());
            if (changed == 1) {
                syncCoverage(rule);
                return;
            }
            if (!jdbc.queryForList("SELECT 1 FROM commercial_reservation.discount_rule WHERE discount_rule_id = ?",
                    rule.ruleId()).isEmpty()) throw new IllegalStateException("Discount rule belongs to another tenant");
            requireServicesOfTheTenant(rule);
            jdbc.update("INSERT INTO commercial_reservation.discount_rule (" + COLUMNS + ") "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rule.ruleId(), rule.tenantId(),
                    rule.name(), rule.validFrom(), rule.validTo(), rule.priority(), rule.calculationBase().name(),
                    rule.percentageBasisPoints() != null ? "PERCENTAGE" : "FIXED_AMOUNT", rule.percentageBasisPoints(),
                    rule.amountCents(), rule.status().name(), rule.serviceCoverage().isAll(), rule.capCents(), rule.stackable(),
                    timestamp(rule.createdAt()), timestamp(rule.updatedAt()));
            for (UUID serviceId : rule.serviceCoverage().serviceIds()) {
                jdbc.update("INSERT INTO commercial_reservation.discount_rule_service "
                        + "(tenant_id, discount_rule_id, service_id) VALUES (?, ?, ?)",
                        rule.tenantId(), rule.ruleId(), serviceId);
            }
        });
    }

    // The application user cannot DELETE (RN-13), so the selected services of an existing rule are moved with UPDATE
    // and added with INSERT. A rows-to-drop surplus cannot be removed, so a selection that drops more services than
    // it adds is refused instead of leaving stale rows that would read back as coverage.
    private void syncCoverage(DiscountRule rule) {
        if (rule.serviceCoverage().isAll()) return; // the rows, if any, are ignored while the flag says "all"
        Set<UUID> selected = rule.serviceCoverage().serviceIds();
        Set<UUID> existing = new TreeSet<>(jdbc.queryForList("SELECT service_id FROM commercial_reservation."
                + "discount_rule_service WHERE tenant_id = ? AND discount_rule_id = ?", UUID.class,
                rule.tenantId(), rule.ruleId()));
        if (existing.equals(selected)) return;
        requireServicesOfTheTenant(rule);
        List<UUID> stale = existing.stream().filter(id -> !selected.contains(id)).toList();
        List<UUID> added = selected.stream().filter(id -> !existing.contains(id)).sorted().toList();
        if (stale.size() > added.size())
            throw new DomainException("serviceCoverage", "a selection cannot drop services of a rule; create a new rule instead");
        for (int i = 0; i < added.size(); i++) {
            if (i < stale.size()) {
                jdbc.update("UPDATE commercial_reservation.discount_rule_service SET service_id = ? "
                        + "WHERE tenant_id = ? AND discount_rule_id = ? AND service_id = ?",
                        added.get(i), rule.tenantId(), rule.ruleId(), stale.get(i));
            } else {
                jdbc.update("INSERT INTO commercial_reservation.discount_rule_service "
                        + "(tenant_id, discount_rule_id, service_id) VALUES (?, ?, ?)",
                        rule.tenantId(), rule.ruleId(), added.get(i));
            }
        }
    }

    // The engine keeps simple foreign keys here (db 013), so a service of another tenant is refused by this adapter.
    private void requireServicesOfTheTenant(DiscountRule rule) {
        Set<UUID> selected = rule.serviceCoverage().serviceIds();
        if (selected.isEmpty()) return;
        List<UUID> owned = jdbc.query("SELECT service_id FROM commercial_reservation.catalog_service "
                + "WHERE tenant_id = ? AND service_id IN (" + "?,".repeat(selected.size() - 1) + "?)",
                (row, index) -> row.getObject("service_id", UUID.class),
                java.util.stream.Stream.concat(java.util.stream.Stream.of((Object) rule.tenantId()), selected.stream())
                        .toArray());
        if (owned.size() != selected.size()) throw new DomainException("serviceIds", "every selected service must belong to the tenant catalog");
    }

    @Override
    public Optional<DiscountRule> findByTenantAndId(UUID tenantId, UUID ruleId) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? AND discount_rule_id = ?", this::restore, tenantId, ruleId)
                .stream().findFirst();
    }

    @Override
    public List<DiscountRule> findByTenant(UUID tenantId) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? ORDER BY created_at DESC, discount_rule_id DESC",
                this::restore, tenantId);
    }

    @Override
    public void append(DiscountRuleChanged event) {
        jdbc.update(OUTBOX, event.tenantId(), event.ruleId(), event.previous() == null ? null : json(event.previous()),
                json(event.current()), event.actorId(), timestamp(event.occurredAt()));
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private DiscountRule restore(ResultSet row, int index) throws SQLException {
        UUID ruleId = row.getObject("discount_rule_id", UUID.class), tenantId = row.getObject("tenant_id", UUID.class);
        ServiceCoverage coverage = row.getBoolean("covers_all_services") ? ServiceCoverage.all()
                : ServiceCoverage.of(Set.copyOf(jdbc.queryForList("SELECT service_id FROM "
                        + "commercial_reservation.discount_rule_service WHERE tenant_id = ? AND discount_rule_id = ?",
                        UUID.class, tenantId, ruleId)));
        return DiscountRule.restore(ruleId, tenantId, row.getString("name"),
                row.getObject("starts_on", java.time.LocalDate.class), row.getObject("ends_on", java.time.LocalDate.class),
                row.getInt("priority"), DiscountBase.valueOf(row.getString("calculation_base")),
                row.getObject("discount_percentage_bp", Integer.class), row.getObject("discount_amount_cents", Long.class),
                coverage, row.getObject("cap_cents", Long.class), row.getBoolean("stackable"), DiscountStatus.valueOf(row.getString("status")),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    // Full state, contract names, text for dates and moments: an auditor reads both sides without the code (plan D-10).
    private static String json(DiscountRuleState state) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ruleId", state.ruleId());
        body.put("tenantId", state.tenantId());
        body.put("name", state.name());
        body.put("validFrom", state.validFrom().toString());
        body.put("validTo", state.validTo().toString());
        body.put("priority", state.priority());
        body.put("calculationBase", state.calculationBase().name());
        body.put("percentageBasisPoints", state.percentageBasisPoints());
        body.put("amountCents", state.amountCents());
        body.put("coversAllServices", state.serviceCoverage().isAll());
        body.put("serviceIds", new TreeSet<>(state.serviceCoverage().serviceIds()));
        body.put("capCents", state.capCents());
        body.put("stackable", state.stackable());
        body.put("status", state.status().name());
        body.put("createdAt", state.createdAt().truncatedTo(ChronoUnit.MICROS).toString());
        body.put("updatedAt", state.updatedAt().truncatedTo(ChronoUnit.MICROS).toString());
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Discount rule state cannot be written", e);
        }
    }

    private static OffsetDateTime timestamp(Instant value) {
        // PostgreSQL rounds excess precision; truncate first to keep the specified microsecond boundary.
        return value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
