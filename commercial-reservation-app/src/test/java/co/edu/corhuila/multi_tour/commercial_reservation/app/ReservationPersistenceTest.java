package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCreatedOutbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;

// HU-COM-007 over the real schema (api tasks T-03, T-05, T-08): every value below is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReservationPersistenceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ReservationPersistenceFixture fixture;
    private JdbcReservationCreatedOutbox outbox;
    private JdbcReservationRepository repository;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        outbox = new JdbcReservationCreatedOutbox(fixture.jdbc);
        repository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, outbox);
    }

    @Test
    void theReservationIsStoredWithItsBirthValuesAndTheCreator() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));

        var row = fixture.jdbc.queryForMap("SELECT * FROM commercial_reservation.reservation WHERE reservation_id = ?", id);
        assertThat(row.get("tenant_id")).isEqualTo(fixture.tenant);
        assertThat(row.get("customer_id")).isEqualTo(fixture.customer);
        assertThat(row.get("salida_id")).isEqualTo(fixture.salida);
        assertThat(row.get("service_id")).isEqualTo(fixture.service);
        assertThat(row.get("party_size")).isEqualTo(3);
        assertThat(row.get("projected_value_cents")).isEqualTo(360_000L);
        assertThat(row.get("final_value_cents")).isEqualTo(324_000L);
        assertThat(row.get("pending_balance_cents")).isEqualTo(324_000L);
        assertThat(row.get("credit_balance_cents")).isEqualTo(0L);
        assertThat(row.get("reservation_status")).isEqualTo("PENDING_PAYMENT");
        assertThat(row.get("payment_status")).isEqualTo("NO_PAYMENT");
        assertThat(row.get("payment_type")).isEqualTo("DEPOSIT");
        assertThat(row.get("payment_method")).isEqualTo("TRANSFER");
        assertThat(row.get("created_by")).isEqualTo(fixture.actor);
    }

    @Test
    void theReservedServiceKeepsTheRatesUsedAndTheTransportIsNullWhenTheTourHasNone() {
        UUID withTransport = UUID.randomUUID();
        UUID without = UUID.randomUUID();
        repository.save(fixture.reservation(withTransport, true));
        repository.save(fixture.reservation(without, false));

        var first = fixture.jdbc.queryForMap(
                "SELECT * FROM commercial_reservation.reserved_service WHERE reservation_id = ?", withTransport);
        assertThat(first.get("service_id")).isEqualTo(fixture.service);
        assertThat(first.get("party_size")).isEqualTo(3);
        assertThat(first.get("base_rate_cents")).isEqualTo(100_000L);
        assertThat(first.get("transport_rate_cents")).isEqualTo(20_000L);
        assertThat(first.get("scheduled_date")).isNull();
        var second = fixture.jdbc.queryForMap(
                "SELECT * FROM commercial_reservation.reserved_service WHERE reservation_id = ?", without);
        assertThat(second.get("transport_rate_cents")).isNull();
    }

    @Test
    void everyRuleInForceIsKeptAsALineEvenWhenItsAmountIsZero() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));

        var lines = fixture.jdbc.queryForList("SELECT * FROM commercial_reservation.reservation_fixed_discount "
                + "WHERE reservation_id = ? ORDER BY line_order", id);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).get("discount_rule_id")).isEqualTo(fixture.percentageRule);
        assertThat(lines.get(0).get("rule_name")).isEqualTo("Early booking");
        assertThat(lines.get(0).get("discount_type")).isEqualTo("PERCENTAGE");
        assertThat(lines.get(0).get("discount_percentage_bp")).isEqualTo(1_000);
        assertThat(lines.get(0).get("discount_amount_cents")).isNull();
        assertThat(lines.get(0).get("calculation_base")).isEqualTo("ORIGINAL_VALUE");
        assertThat(lines.get(0).get("base_cents")).isEqualTo(360_000L);
        assertThat(lines.get(0).get("amount_cents")).isEqualTo(36_000L);
        assertThat(lines.get(0).get("subtotal_cents")).isEqualTo(324_000L);
        assertThat(lines.get(0).get("cap_cents")).isEqualTo(50_000L);
        assertThat(lines.get(1).get("discount_type")).isEqualTo("FIXED_AMOUNT");
        assertThat(lines.get(1).get("discount_amount_cents")).isEqualTo(5_000L);
        assertThat(lines.get(1).get("calculation_base")).isEqualTo("PREVIOUS_SUBTOTAL");
        assertThat(lines.get(1).get("amount_cents")).isEqualTo(0L);
        assertThat(lines.get(1).get("cap_cents")).isNull();
    }

    @Test
    void theCompanionsAreLinkedAndTheirSensitiveValuesAreStoredOnlyEncrypted() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true, fixture.plainCompanion(), fixture.riskyCompanion()));

        var rows = fixture.jdbc.queryForList("SELECT * FROM commercial_reservation.reservation_companion "
                + "WHERE reservation_id = ? ORDER BY document_number", id);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("tenant_id")).isEqualTo(fixture.tenant);
        assertThat(rows.get(0).get("full_name")).isEqualTo(ReservationPersistenceFixture.COMPANION_NAME);
        assertThat(rows.get(0).get("document_number")).isEqualTo(ReservationPersistenceFixture.COMPANION_DOCUMENT);
        assertThat(String.valueOf(rows.get(1).get("birth_date_or_age"))).contains("v1:test1:").doesNotContain("1990-05-17");
        var sensitive = fixture.jdbc.queryForList("SELECT s.activity_requirements::text AS body FROM "
                + "commercial_reservation.reservation_companion_sensitive_data s JOIN "
                + "commercial_reservation.reservation_companion c ON c.reservation_companion_id = s.reservation_companion_id "
                + "WHERE c.reservation_id = ?", id);
        assertThat(sensitive).hasSize(1);
        assertThat(String.valueOf(sensitive.get(0).get("body"))).contains("v1:test1:")
                .doesNotContain(ReservationPersistenceFixture.BLOOD, "Contacto Ficticio");
    }

    @Test
    void reservationCreatedIsInTheOutboxWithReferencesOnly() throws Exception {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true, fixture.riskyCompanion()));

        var events = fixture.jdbc.queryForList("SELECT * FROM commercial_reservation.outbox_event WHERE reservation_id = ?", id);
        assertThat(events).hasSize(1);
        var event = events.get(0);
        assertThat(event.get("event_type")).isEqualTo("ReservationCreated");
        assertThat(event.get("tenant_id")).isEqualTo(fixture.tenant);
        assertThat(event.get("actor_id")).isEqualTo(fixture.actor);
        assertThat(event.get("service_id")).isNull();
        assertThat(event.get("published_at")).isNull();
        JsonNode payload = JSON.readTree(event.get("payload").toString());
        Set<String> keys = new TreeSet<>();
        payload.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("customerId", "finalValueCents", "paymentStatus", "projectedValueCents",
                "reservationStatus", "serviceId");
        assertThat(payload.get("customerId").asText()).isEqualTo(fixture.customer.toString());
        assertThat(payload.get("serviceId").asText()).isEqualTo(fixture.service.toString());
        assertThat(payload.get("projectedValueCents").asLong()).isEqualTo(360_000L);
        assertThat(payload.get("finalValueCents").asLong()).isEqualTo(324_000L);
        assertThat(payload.get("reservationStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(payload.get("paymentStatus").asText()).isEqualTo("NO_PAYMENT");
        assertThat(payload.toString()).doesNotContain(ReservationPersistenceFixture.BLOOD, "DOC-FICT", "Ficticio");
    }

    @Test
    void aFailingEventLeavesNoRowInAnyTable() {
        UUID id = UUID.randomUUID();
        ReservationCreatedOutbox failing = event -> {
            outbox.append(event);
            throw new IllegalStateException("broker down");
        };
        var failingRepository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, failing);

        assertThatThrownBy(() -> failingRepository.save(fixture.reservation(id, true, fixture.riskyCompanion())))
                .isInstanceOf(IllegalStateException.class);

        assertNothingStored(id);
    }

    @Test
    void aLineWhoseRuleDoesNotExistLeavesNoRowInAnyTable() {
        UUID id = UUID.randomUUID();
        var reservation = fixture.reservationWithUnknownRule(id);

        assertThatThrownBy(() -> repository.save(reservation)).isInstanceOf(DataAccessException.class);

        assertNothingStored(id);
    }

    private void assertNothingStored(UUID id) {
        for (String table : new String[] {"reservation", "reserved_service", "reservation_fixed_discount",
                "reservation_companion", "outbox_event"}) {
            assertThat(fixture.count(table, id)).as(table).isZero();
        }
    }

    private static boolean present(String name) {
        return System.getenv(name) != null;
    }
}
