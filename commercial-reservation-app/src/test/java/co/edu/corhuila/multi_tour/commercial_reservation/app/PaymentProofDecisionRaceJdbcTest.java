package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-010 RNF-06 (audit of 2026-11-06): decisions that arrive at the same moment through the real database. The decision of a proof
// takes the row lock of its reservation, so the second of two decisions on one proof gets the final refusal (422 INVALID_STATUS_TRANSITION,
// never a 500 from the guard of the table) and two approvals of different proofs of one reservation are both applied (no lost update).
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
@Import(PaymentProofDecisionRaceJdbcTest.FixedClock.class)
class PaymentProofDecisionRaceJdbcTest {
    static final String BASE = "/api/v1/commercial-reservation";
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    static final int ROUNDS = 8;

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock proofRaceClock() { return Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC); }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogServiceRepository catalog;
    @Autowired CustomerRepository customers;

    final UUID tenant = UUID.randomUUID(), tour = UUID.randomUUID(), customer = UUID.randomUUID();

    @BeforeEach void seed() throws Exception {
        OffsetDateTime now = OffsetDateTime.ofInstant(TestJwtKeys.NOW, ZoneOffset.UTC);
        jdbc.update("INSERT INTO commercial_reservation.tenant_status (tenant_id, identifier, status, time_zone, country, "
                + "last_event_occurred_at, created_at, updated_at) VALUES (?, ?, 'ACTIVE', 'America/Bogota', 'CO', ?, ?, ?)",
                tenant, "t-" + tenant, now, now, now);
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio",
                CommercialTerms.of(100_000L, 40_000L, 20, false, false), TestJwtKeys.NOW));
        customers.save(Customer.registerLead(customer, tenant, "CC12345", "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("persona@example.invalid", "3000000000"), true, TestJwtKeys.NOW));
        var row = Map.of("paymentType", "DEPOSIT", "initialWaitHours", 24, "additionalWaitHours", 48, "minimumDepositBp", 3000,
                "transferEnabled", true, "cashEnabled", true);
        send(put(BASE + "/payment-parameters").header("Idempotency-Key", "race-param-" + tenant).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("reason", "Opening season", "parameters", List.of(row)))), 200);
    }

    private String bearer() throws Exception {
        return "Bearer " + CatalogHttpTest.token(tenant.toString(), "TENANT_ADMINISTRATOR");
    }

    private String send(MockHttpServletRequestBuilder request, int expected) throws Exception {
        MockHttpServletResponse response = mvc.perform(request.header("Authorization", bearer())
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString();
    }

    private UUID salida() {
        UUID salida = UUID.randomUUID();
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, "
                + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 4)", UUID.randomUUID(), tenant,
                salida, tour, OffsetDateTime.ofInstant(TestJwtKeys.NOW, ZoneOffset.UTC).plusDays(1));
        return salida;
    }

    private String reservation(UUID salida, String key) throws Exception {
        var body = Map.of("serviceId", tour.toString(), "salidaId", salida.toString(), "people", 3, "customerId", customer.toString(),
                "paymentType", "DEPOSIT", "paymentMethod", "TRANSFER");
        return TestJwtKeys.JSON.readTree(send(post(BASE + "/reservations").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body)), 201)).get("reservationId").asText();
    }

    private String transfer(String reservation, long amount, String key) throws Exception {
        return TestJwtKeys.JSON.readTree(send(multipart(BASE + "/reservations/" + reservation + "/payments")
                .file(new MockMultipartFile("payment", "", "application/json",
                        TestJwtKeys.JSON.writeValueAsBytes(Map.of("paymentMethod", "TRANSFER", "amountCents", amount))))
                .file(new MockMultipartFile("proof", "proof.png", "image/png", PNG)).header("Idempotency-Key", key), 201)).get("paymentId").asText();
    }

    private record Outcome(int status, String error) {}

    private Outcome decide(CountDownLatch start, String proof, String action, Map<String, Object> body, String key) throws Exception {
        start.await();
        MockHttpServletResponse response = mvc.perform(post(BASE + "/payment-proofs/" + proof + "/" + action).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body)).header("Authorization", bearer())
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn().getResponse();
        String content = response.getContentAsString();
        return new Outcome(response.getStatus(), content.isBlank() ? "" : TestJwtKeys.JSON.readTree(content).path("error").asText());
    }

    private List<Outcome> together(CountDownLatch start, List<Callable<Outcome>> calls) throws Exception {
        var pool = Executors.newFixedThreadPool(calls.size());
        try {
            var futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (var future : futures) outcomes.add(future.get());
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Map<String, Object> body(String action) {
        return action.equals("approve") ? Map.of("verifiedAmountCents", 90_000L) : Map.of("reason", "Illegible proof");
    }

    // RNF-06: two decisions on the SAME proof with different keys, at the same moment, in every combination of the two actions.
    @Test void twoDecisionsOnTheSameProofLeaveExactlyOneWinnerAndTheOtherGetsTheFinalRefusal() throws Exception {
        String[][] pairs = {{"approve", "approve"}, {"approve", "reject"}, {"reject", "approve"}, {"reject", "reject"}};
        for (int round = 0; round < ROUNDS; round++) {
            String[] pair = pairs[round % pairs.length];
            String reservation = reservation(salida(), "race-same-" + round + "-0");
            String proof = transfer(reservation, 90_000, "race-same-" + round + "-1");
            var start = new CountDownLatch(1);
            int r = round;

            List<Outcome> outcomes = together(start, List.of(
                    () -> decide(start, proof, pair[0], body(pair[0]), "race-same-" + r + "-a"),
                    () -> decide(start, proof, pair[1], body(pair[1]), "race-same-" + r + "-b")));

            assertThat(outcomes).as("round %d %s/%s", round, pair[0], pair[1]).extracting(Outcome::status).containsExactlyInAnyOrder(200, 422);
            assertThat(outcomes).filteredOn(o -> o.status() == 422).extracting(Outcome::error).containsExactly("INVALID_STATUS_TRANSITION");
            assertThat(jdbc.queryForObject("SELECT validation_status FROM commercial_reservation.payment_attempt WHERE payment_attempt_id = ?",
                    String.class, UUID.fromString(proof))).isIn("APPROVED", "REJECTED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                    + "AND event_type IN ('PaymentValidated', 'PaymentRejected')", Integer.class, UUID.fromString(reservation))).isEqualTo(1);
        }
    }

    // Two approvals of different proofs of one reservation: both are applied, and the balance and the situation equal those of the
    // same two approvals made one after the other on a twin reservation.
    @Test void twoApprovalsOfDifferentProofsOfOneReservationAreBothApplied() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID salida = salida();
            String reservation = reservation(salida, "race-two-" + round + "-0"), twin = reservation(salida, "race-two-" + round + "-9");
            String big = transfer(reservation, 90_000, "race-two-" + round + "-1"), small = transfer(reservation, 20_000, "race-two-" + round + "-2");
            String twinBig = transfer(twin, 90_000, "race-two-" + round + "-3"), twinSmall = transfer(twin, 20_000, "race-two-" + round + "-4");
            var start = new CountDownLatch(1);
            var none = new CountDownLatch(0);
            int r = round;

            List<Outcome> outcomes = together(start, List.of(
                    () -> decide(start, big, "approve", Map.of("verifiedAmountCents", 90_000L), "race-two-" + r + "-a"),
                    () -> decide(start, small, "approve", Map.of("verifiedAmountCents", 20_000L), "race-two-" + r + "-b")));
            decide(none, twinBig, "approve", Map.of("verifiedAmountCents", 90_000L), "race-two-" + round + "-c");
            decide(none, twinSmall, "approve", Map.of("verifiedAmountCents", 20_000L), "race-two-" + round + "-d");

            assertThat(outcomes).as("round %d", round).extracting(Outcome::status).containsExactly(200, 200);
            var row = jdbc.queryForMap("SELECT reservation_status, payment_status, pending_balance_cents, final_value_cents FROM commercial_reservation.reservation "
                    + "WHERE tenant_id = ? AND reservation_id = ?", tenant, UUID.fromString(reservation));
            var reference = jdbc.queryForMap("SELECT reservation_status, payment_status, pending_balance_cents FROM commercial_reservation.reservation "
                    + "WHERE tenant_id = ? AND reservation_id = ?", tenant, UUID.fromString(twin));
            assertThat(((Number) row.get("pending_balance_cents")).longValue())
                    .isEqualTo(((Number) row.get("final_value_cents")).longValue() - 110_000L);
            assertThat(row.get("pending_balance_cents")).isEqualTo(reference.get("pending_balance_cents"));
            assertThat(row.get("payment_status")).isEqualTo(reference.get("payment_status"));
            assertThat(row.get("reservation_status")).isEqualTo("CONFIRMED").isEqualTo(reference.get("reservation_status"));
            assertThat(jdbc.queryForObject("SELECT sum(applied_amount_cents) FROM commercial_reservation.payment_attempt WHERE reservation_id = ? "
                    + "AND validation_status = 'APPROVED'", Long.class, UUID.fromString(reservation))).isEqualTo(110_000L);
        }
    }
}
