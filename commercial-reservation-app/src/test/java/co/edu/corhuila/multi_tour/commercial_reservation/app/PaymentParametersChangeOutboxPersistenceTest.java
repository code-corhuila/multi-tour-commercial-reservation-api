package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentParametersChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameterValues;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParametersChange;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// HU-COM-009 DP-04 (migration 044 of the -db repo): a change of a parameter row is one row of the outbox, with the changed payment_condition row as aggregate, the administrator as actor and the
// closed payload, with no value of a parameter and no reason. Runs against the real database only (it needs the three TEST_DATABASE variables), like the other persistence tests.
class PaymentParametersChangeOutboxPersistenceTest {
    private static final Instant AT = Instant.parse("2026-11-12T10:00:00Z");
    private ReservationPersistenceFixture fixture;
    private JdbcPaymentParameterRepository parameters;
    private JdbcPaymentParametersChangeOutbox outbox;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        parameters = new JdbcPaymentParameterRepository(fixture.jdbc);
        outbox = new JdbcPaymentParametersChangeOutbox(fixture.jdbc);
    }

    @Test
    void aChangedRowIsWrittenToTheOutboxWithTheRowAsAggregateAndTheClosedPayload() {
        parameters.save(fixture.tenant, new PaymentParameter(new PaymentParameterValues(PaymentType.DEPOSIT, fixture.service, 24, 48, 3_000, 72, true, false),
                fixture.actor, "A secret reason", AT));

        outbox.append(new PaymentParametersChange(fixture.tenant, fixture.actor, PaymentType.DEPOSIT, fixture.service, AT));

        List<Map<String, Object>> rows = fixture.jdbc.queryForList("SELECT e.event_type, e.tenant_id, e.actor_id, e.occurred_at, e.payload::text AS payload, "
                + "(e.payment_condition_id = c.payment_condition_id) AS names_the_row FROM commercial_reservation.outbox_event e "
                + "JOIN commercial_reservation.payment_condition c ON c.payment_condition_id = e.payment_condition_id WHERE c.tenant_id = ? AND c.service_id = ?",
                fixture.tenant, fixture.service);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("event_type")).isEqualTo("PaymentParametersChanged");
        assertThat(row.get("tenant_id")).isEqualTo(fixture.tenant);
        assertThat(row.get("actor_id")).isEqualTo(fixture.actor);
        assertThat(row.get("names_the_row")).isEqualTo(true);
        assertThat(((java.sql.Timestamp) row.get("occurred_at")).toInstant()).isEqualTo(AT);
        assertThat((String) row.get("payload")).contains("\"paymentType\": \"DEPOSIT\"").contains("\"serviceId\": \"" + fixture.service + "\"")
                .contains("\"changedAt\": \"2026-11-12T10:00:00Z\"").doesNotContain("3000").doesNotContain("secret").doesNotContain("reason");
    }

    @Test
    void theTenantLevelEventCarriesNoServiceKeyAndAnEventForAMissingRowIsRefused() {
        parameters.save(fixture.tenant, new PaymentParameter(new PaymentParameterValues(PaymentType.FULL_PAYMENT, null, 24, 48, null, null, true, true),
                fixture.actor, "Tenant level", AT));

        outbox.append(new PaymentParametersChange(fixture.tenant, fixture.actor, PaymentType.FULL_PAYMENT, null, AT));

        String payload = fixture.jdbc.queryForObject("SELECT e.payload::text FROM commercial_reservation.outbox_event e JOIN commercial_reservation.payment_condition c "
                + "ON c.payment_condition_id = e.payment_condition_id WHERE c.tenant_id = ? AND c.service_id IS NULL", String.class, fixture.tenant);
        assertThat(payload).contains("\"paymentType\": \"FULL_PAYMENT\"").doesNotContain("serviceId");
        assertThatThrownBy(() -> outbox.append(new PaymentParametersChange(UUID.randomUUID(), fixture.actor, PaymentType.DEPOSIT, null, AT)))
                .isInstanceOf(IllegalStateException.class);
    }
}
