package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-019 over HTTP with every real JDBC bean (api tasks T-05, T-07; AC-19.9, AC-19.11 to AC-19.13, AC-19.15): the internal routes accept only a service token, the tenant
// is the path, an inactive tenant is still deleted, the due list carries identifiers and the basis only and is paged, a deletion of another tenant touches nothing and a
// reservation already deleted never comes back in the list. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
@Import(CreditBalanceWriteJdbcHttpTest.FixedClock.class)
class SensitiveDataRetentionJdbcHttpTest {
    static final String BASE = "/api/v1/commercial-reservation";
    static final String ADMIN = "TENANT_ADMINISTRATOR";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogServiceRepository catalog;
    @Autowired CustomerRepository customers;

    final UUID tenant = UUID.randomUUID(), tour = UUID.randomUUID(), departure = UUID.randomUUID(), customer = UUID.randomUUID();
    String first, second;

    @BeforeEach void seed() throws Exception {
        OffsetDateTime now = OffsetDateTime.ofInstant(TestJwtKeys.NOW, ZoneOffset.UTC), longAgo = now.minusDays(400);
        jdbc.update("INSERT INTO commercial_reservation.tenant_status (tenant_id, identifier, status, time_zone, country, "
                + "last_event_occurred_at, created_at, updated_at) VALUES (?, ?, 'ACTIVE', 'America/Bogota', 'CO', ?, ?, ?)", tenant, "t-" + tenant, now, now, now);
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio", CommercialTerms.of(100_000L, 40_000L, 20, false, false), TestJwtKeys.NOW));
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, hora_inicio, status, effective_capacity, held_seats) "
                + "VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 4)", UUID.randomUUID(), tenant, departure, tour, now.plusDays(1));
        customers.save(Customer.registerLead(customer, tenant, "CC12345", "Persona Ficticia", BirthDateOrAge.ofAge(30), new ContactInfo("persona@example.invalid", "3000000000"),
                true, TestJwtKeys.NOW));
        var row = Map.of("paymentType", "DEPOSIT", "initialWaitHours", 24, "additionalWaitHours", 48, "minimumDepositBp", 3000, "transferEnabled", true, "cashEnabled", true);
        exchange(put(BASE + "/payment-parameters").header("Idempotency-Key", "param-retention-01").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("reason", "Opening season", "parameters", List.of(row)))), token(ADMIN), 200);
        first = reservation("retention-reservation-1");
        second = reservation("retention-reservation-2");
        jdbc.update("UPDATE commercial_reservation.salida_capacity SET status = 'FINALIZED', estimated_ends_at = ?, finalized_at = ? WHERE tenant_id = ? AND salida_id = ?", longAgo, longAgo, tenant, departure);
        for (int index = 0; index < 2; index++) {
            UUID companion = UUID.randomUUID();
            jdbc.update("INSERT INTO commercial_reservation.reservation_companion (reservation_companion_id, tenant_id, reservation_id, full_name, document_number, birth_date_or_age) "
                    + "VALUES (?, ?, ?::uuid, 'Acompanante Ficticio', 'DOC-FICT-" + index + "', 'envelope')", companion, tenant, first);
            jdbc.update("INSERT INTO commercial_reservation.reservation_companion_sensitive_data (reservation_companion_id, tenant_id, activity_requirements) "
                    + "VALUES (?, ?, '{\"envelope\":\"x\"}'::jsonb)", companion, tenant);
        }
        jdbc.update("INSERT INTO commercial_reservation.customer_sensitive_data (customer_id, tenant_id, emergency_info) VALUES (?, ?, '{\"envelope\":\"x\"}'::jsonb)", customer, tenant);
    }

    MvcResult exchange(MockHttpServletRequestBuilder request, String bearer, int code) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    String token(String role) throws Exception { return CatalogHttpTest.token(tenant.toString(), role); }

    String service() throws Exception { return CustomerHttpTest.token(null, "SERVICE", List.of()); }

    String reservation(String key) throws Exception {
        var body = Map.of("serviceId", tour.toString(), "salidaId", departure.toString(), "people", 3, "customerId", customer.toString(), "paymentType", "DEPOSIT",
                "paymentMethod", "TRANSFER");
        return CatalogHttpTest.json(exchange(post(BASE + "/reservations").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body)), token(ADMIN), 201)).get("reservationId").asText();
    }

    MockHttpServletRequestBuilder delete(UUID forTenant, String reservation, String key) {
        var request = post(BASE + "/internal/tenants/" + forTenant + "/reservations/" + reservation + "/sensitive-data-deletion");
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    MockHttpServletRequestBuilder due(UUID forTenant, String query) {
        return get(BASE + "/internal/tenants/" + forTenant + "/sensitive-data-retention/due" + query);
    }

    int records() {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.sensitive_data_deletion WHERE tenant_id = ?", Integer.class, tenant);
    }

    @Test void theInternalRoutesAcceptOnlyAServiceToken() throws Exception {
        exchange(delete(tenant, first, "delete-key-0001"), token(ADMIN), 403);
        exchange(due(tenant, ""), token(ADMIN), 403);

        exchange(due(tenant, ""), service(), 200);
        assertThat(records()).isZero();
    }

    @Test void theDueListCarriesIdsAndBasisOnlyAndIsPaged() throws Exception {
        var page = CatalogHttpTest.json(exchange(due(tenant, "?limit=1"), service(), 200));

        assertThat(page.get("data")).hasSize(1);
        JsonNode row = page.get("data").get(0);
        List<String> keys = new ArrayList<>();
        row.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("reservationId", "customerId", "anchorBasis", "anchorAt");
        assertThat(row.get("anchorBasis").asText()).isEqualTo("SERVICE_END");
        String next = page.get("next").asText();

        var rest = CatalogHttpTest.json(exchange(due(tenant, "?limit=1&after=" + next), service(), 200));
        assertThat(rest.get("data")).hasSize(1);
        assertThat(rest.get("data").get(0).get("reservationId").asText()).isNotEqualTo(row.get("reservationId").asText());
        CatalogHttpTest.assertField(exchange(due(tenant, "?limit=101"), service(), 400), "limit");
    }

    @Test void theDeletionAnswersTheOutcomeAndARetryLeavesOneRecord() throws Exception {
        var done = CatalogHttpTest.json(exchange(delete(tenant, first, "delete-key-0002"), service(), 200));
        var again = CatalogHttpTest.json(exchange(delete(tenant, first, "delete-key-0002"), service(), 200));

        assertThat(done.get("outcome").asText()).isEqualTo("DELETED");
        assertThat(done.get("companionRowsDeleted").asInt()).isEqualTo(2);
        assertThat(done.get("customerDataDeleted").asBoolean()).isTrue();
        assertThat(again.get("outcome").asText()).isEqualTo("ALREADY_DELETED");
        assertThat(records()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE tenant_id = ? AND event_type = 'SensitiveDataDeleted'", Integer.class, tenant))
                .isEqualTo(1);
        CatalogHttpTest.assertField(exchange(delete(tenant, first, null), service(), 400), "Idempotency-Key");
    }

    @Test void aDeletionOfAnotherTenantOrAMissingReservationTouchesNothing() throws Exception {
        exchange(delete(UUID.randomUUID(), first, "delete-key-0003"), service(), 404);
        exchange(delete(tenant, UUID.randomUUID().toString(), "delete-key-0004"), service(), 404);

        assertThat(records()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.customer_sensitive_data WHERE tenant_id = ?", Integer.class, tenant)).isEqualTo(1);
    }

    @Test void anInactiveTenantIsStillDeleted() throws Exception {
        jdbc.update("UPDATE commercial_reservation.tenant_status SET status = 'INACTIVE' WHERE tenant_id = ?", tenant);

        var done = CatalogHttpTest.json(exchange(delete(tenant, first, "delete-key-0005"), service(), 200));

        assertThat(done.get("outcome").asText()).isEqualTo("DELETED");
    }

    // AC-19.15 (T-07): a backup taken before the deletion is restored, so the sensitive rows are back and the record of the deletion is gone. Restoring is an act of the database
    // administrator: the application user holds no DELETE on the record (the schema refuses it), so this one step needs the privileged connection of the test environment.
    @Test void aRestoredSnapshotIsDeletedAgainByTheSameCall() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_ADMIN_USER") != null && System.getenv("TEST_DATABASE_ADMIN_PASSWORD") != null,
                "The restore of a snapshot needs TEST_DATABASE_ADMIN_USER and TEST_DATABASE_ADMIN_PASSWORD");
        var admin = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"), System.getenv("TEST_DATABASE_ADMIN_USER"),
                System.getenv("TEST_DATABASE_ADMIN_PASSWORD")));
        var before = CatalogHttpTest.json(exchange(delete(tenant, first, "delete-key-0007"), service(), 200));
        assertThat(before.get("outcome").asText()).isEqualTo("DELETED");
        assertThat(sensitiveRows()).isZero();

        // The restore: the rows of the snapshot are back (the data was only deleted after it) and the snapshot has no record of the deletion.
        for (int index = 0; index < 2; index++) {
            UUID companion = jdbc.queryForObject("SELECT reservation_companion_id FROM commercial_reservation.reservation_companion WHERE tenant_id = ? AND reservation_id = ?::uuid "
                    + "AND document_number = 'DOC-FICT-" + index + "'", UUID.class, tenant, first);
            jdbc.update("INSERT INTO commercial_reservation.reservation_companion_sensitive_data (reservation_companion_id, tenant_id, activity_requirements) VALUES (?, ?, '{\"envelope\":\"x\"}'::jsonb)", companion, tenant);
        }
        jdbc.update("INSERT INTO commercial_reservation.customer_sensitive_data (customer_id, tenant_id, emergency_info) VALUES (?, ?, '{\"envelope\":\"x\"}'::jsonb)", customer, tenant);
        assertThat(admin.update("DELETE FROM commercial_reservation.sensitive_data_deletion WHERE tenant_id = ? AND reservation_id = ?::uuid", tenant, first)).isEqualTo(1);
        assertThat(sensitiveRows()).isEqualTo(3);
        assertThat(records()).isZero();

        var again = CatalogHttpTest.json(exchange(delete(tenant, first, "delete-key-0008"), service(), 200));

        assertThat(again.get("outcome").asText()).isEqualTo("DELETED");
        assertThat(again.get("companionRowsDeleted").asInt()).isEqualTo(2);
        assertThat(again.get("customerDataDeleted").asBoolean()).isTrue();
        assertThat(sensitiveRows()).isZero();
        assertThat(records()).isEqualTo(1);
        // Whether the second deletion must emit its own event is not decided (open question to the Project Lead), so the number of events is not asserted.
    }

    int sensitiveRows() {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM commercial_reservation.reservation_companion_sensitive_data WHERE tenant_id = ?) + "
                + "(SELECT count(*) FROM commercial_reservation.customer_sensitive_data WHERE tenant_id = ?)", Integer.class, tenant, tenant);
    }

    @Test void theDueListNeverReturnsADeletedReservation() throws Exception {
        exchange(delete(tenant, first, "delete-key-0006"), service(), 200);

        var page = CatalogHttpTest.json(exchange(due(tenant, ""), service(), 200));

        assertThat(page.get("data")).hasSize(1);
        assertThat(page.get("data").get(0).get("reservationId").asText()).isEqualTo(second);
    }
}
