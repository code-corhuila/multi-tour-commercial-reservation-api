package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-018 api spec T-07, AC-18.20 (plan risk "an answer and an expiry both win"): an answer, an expiry and a voiding that reach one episode at the same moment, through every real JDBC bean
// and the real database, leave exactly one transition. The technique is the one of HU-COM-009 AC-09.7 (PaymentJdbcHttpTest): racing threads released by one latch, six rounds, no answer a 500.
// Each racing thread reads its own clock (a shared mutable instant would hide the race): the thread of an expiry reads an instant after the response deadline, the others an instant before it, as
// two requests that were stamped a moment apart would. The outcome is read from the rows and must be one of the three consistent ones, with no double effect: no second record, no second event,
// no seat held twice, no hold left behind, no seat on the proposed departure of a cancelled reservation. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
@Import(ReschedulingRacesJdbcHttpTest.PerThreadClock.class)
class ReschedulingRacesJdbcHttpTest {
    private static final String BASE = "/api/v1/commercial-reservation";
    private static final String ADMIN = "TENANT_ADMINISTRATOR", COLLABORATOR = "OPERATIONAL_COLLABORATOR", FINAL_CUSTOMER = "FINAL_CUSTOMER";
    private static final Instant NOW = TestJwtKeys.NOW;
    private static final Instant AFTER_THE_DEADLINE = NOW.plusSeconds(60);
    private static final Instant PROPOSED_START = NOW.plusSeconds(30);
    private static final UUID ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT), STAFF = new UUID(0, 0x9E1);
    private static final int ROUNDS = 6;

    // The clock of the test: every thread reads the instant it was given, or the fixed one.
    @TestConfiguration
    static class PerThreadClock {
        static final ThreadLocal<Instant> AT = new ThreadLocal<>();

        @Bean @Primary Clock reschedulingRaceClock() {
            return new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { Instant at = AT.get(); return at == null ? NOW : at; }
            };
        }
    }

    private enum Outcome { ACCEPTED, REJECTED, EXPIRED, VOIDED }

    private record Result(int status, JsonNode body) {
        boolean won() { return status == 200 || status == 201; }
    }

    private record Scenario(UUID reservation, UUID episode, UUID proposal, UUID proposed) {
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogServiceRepository catalog;
    @Autowired CustomerRepository customers;
    @Autowired ReschedulingRepository episodes;
    @Autowired SalidaCapacityHolder holder;

    private final UUID tenant = UUID.randomUUID(), tour = UUID.randomUUID(), departure = UUID.randomUUID(), customer = UUID.randomUUID();
    private int serial;

    @BeforeEach void seed() throws Exception {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        jdbc.update("INSERT INTO commercial_reservation.tenant_status (tenant_id, identifier, status, time_zone, country, last_event_occurred_at, created_at, updated_at) "
                + "VALUES (?, ?, 'ACTIVE', 'America/Bogota', 'CO', ?, ?, ?)", tenant, "t-" + tenant, now, now, now);
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio", CommercialTerms.of(100_000L, 40_000L, 20, false, false), NOW));
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, hora_inicio, status, effective_capacity, held_seats) "
                + "VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 4)", UUID.randomUUID(), tenant, departure, tour, now.plusDays(1));
        customers.save(Customer.registerLead(customer, tenant, "CC12345", "Persona Ficticia", BirthDateOrAge.ofAge(30), new ContactInfo("persona@example.invalid", "3000000000"), true, NOW)
                .linkedTo(ACCOUNT));
        var row = Map.of("paymentType", "DEPOSIT", "initialWaitHours", 24, "additionalWaitHours", 48, "minimumDepositBp", 3000, "transferEnabled", true, "cashEnabled", true);
        mvc.perform(put(BASE + "/payment-parameters").header("Idempotency-Key", "race-param-0001").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("reason", "Opening season", "parameters", List.of(row)))).header("Authorization", "Bearer " + staff(ADMIN))
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
    }

    private String staff(String role) throws Exception { return CatalogHttpTest.token(tenant.toString(), role); }

    private String service() throws Exception { return CustomerHttpTest.token(null, "SERVICE", List.of()); }

    private String customerToken() throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("sub", ACCOUNT.toString()); claims.put("tenantId", tenant.toString()); claims.put("roles", List.of(FINAL_CUSTOMER));
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    // A reservation of the original departure, an open episode with one pending proposal for a fresh departure whose response deadline is PROPOSED_START, and the three held seats of the proposal.
    private Scenario plant() throws Exception {
        serial++;
        var body = Map.of("serviceId", tour.toString(), "salidaId", departure.toString(), "people", 3, "customerId", customer.toString(), "paymentType", "DEPOSIT", "paymentMethod", "TRANSFER");
        var created = mvc.perform(post(BASE + "/reservations").header("Idempotency-Key", "race-res-" + serial).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body)).header("Authorization", "Bearer " + staff(ADMIN)).header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        UUID reservation = UUID.fromString(CatalogHttpTest.json(created).get("reservationId").asText());
        UUID proposed = UUID.randomUUID(), episode = UUID.randomUUID(), proposal = UUID.randomUUID();
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, hora_inicio, status, effective_capacity, held_seats) "
                + "VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 0)", UUID.randomUUID(), tenant, proposed, tour, OffsetDateTime.ofInstant(PROPOSED_START, ZoneOffset.UTC));
        episodes.save(ReschedulingEpisode.open(episode, tenant, reservation, departure, NOW.minusSeconds(7_200), NOW.minusSeconds(7_000))
                .propose(proposal, proposed, 3, STAFF, NOW.minusSeconds(3_600), PROPOSED_START));
        assertThat(holder.holdForRescheduling(tenant, reservation, proposed, 3)).isTrue();
        return new Scenario(reservation, episode, proposal, proposed);
    }

    private Result call(Instant at, MockHttpServletRequestBuilder request) throws Exception {
        PerThreadClock.AT.set(at);
        try {
            var response = mvc.perform(request.header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn().getResponse();
            String text = response.getContentAsString();
            return new Result(response.getStatus(), text.isBlank() ? null : TestJwtKeys.JSON.readTree(text));
        } finally {
            PerThreadClock.AT.remove();
        }
    }

    private MockHttpServletRequestBuilder customerAnswer(Scenario s, String decision, String key) throws Exception {
        return post(BASE + "/reschedulings/" + s.episode + "/proposals/" + s.proposal + "/answer").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("decision", decision))).header("Idempotency-Key", key).header("Authorization", "Bearer " + customerToken());
    }

    private MockHttpServletRequestBuilder outsideAnswer(Scenario s, String decision, String key) throws Exception {
        return post(BASE + "/reschedulings/" + s.episode + "/proposals/" + s.proposal + "/external-answer").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("decision", decision, "channel", "PHONE", "evidence", "Llamada ficticia", "answeredAt", NOW.toString())))
                .header("Idempotency-Key", key).header("Authorization", "Bearer " + staff(COLLABORATOR));
    }

    private MockHttpServletRequestBuilder expiry(Scenario s) throws Exception {
        return post(BASE + "/internal/tenants/" + tenant + "/reschedulings/" + s.episode + "/expiry").header("Authorization", "Bearer " + service());
    }

    private MockHttpServletRequestBuilder voiding(Scenario s, String key) throws Exception {
        return post(BASE + "/internal/tenants/" + tenant + "/reschedulings/proposed-departure-cancellations").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("departureId", s.proposed.toString(), "cancelledAt", NOW.toString()))).header("Idempotency-Key", key)
                .header("Authorization", "Bearer " + service());
    }

    private List<Result> race(List<Callable<Result>> calls) throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (Callable<Result> one : calls) futures.add(pool.submit(() -> { start.await(); return one.call(); }));
            start.countDown();
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) results.add(future.get());
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }

    private int events(UUID reservation, String type) {
        return count("SELECT count(*) FROM commercial_reservation.outbox_event WHERE tenant_id = ? AND reservation_id = ? AND event_type = ?", tenant, reservation, type);
    }

    private Outcome outcomeOf(Scenario s) {
        String state = jdbc.queryForObject("SELECT episode_state FROM commercial_reservation.rescheduling_episode WHERE tenant_id = ? AND rescheduling_episode_id = ?", String.class, tenant, s.episode);
        String reason = jdbc.queryForObject("SELECT closed_reason FROM commercial_reservation.rescheduling_episode WHERE tenant_id = ? AND rescheduling_episode_id = ?", String.class, tenant, s.episode);
        if (state.equals("RESCHEDULED")) return Outcome.ACCEPTED;
        if (state.equals("OPEN")) return Outcome.VOIDED;
        return "REJECTED_BY_CUSTOMER".equals(reason) ? Outcome.REJECTED : Outcome.EXPIRED;
    }

    // One of the four consistent endings, and nothing of any other: the effects of the winner are all there, the effects of the others are none.
    private Outcome consistent(Scenario s) {
        Outcome outcome = outcomeOf(s);
        String reservationState = jdbc.queryForObject("SELECT reservation_status FROM commercial_reservation.reservation WHERE tenant_id = ? AND reservation_id = ?", String.class, tenant, s.reservation);
        UUID reservationDeparture = jdbc.queryForObject("SELECT salida_id FROM commercial_reservation.reservation WHERE tenant_id = ? AND reservation_id = ?", UUID.class, tenant, s.reservation);
        int held = count("SELECT held_seats FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?", tenant, s.proposed);
        int seats = count("SELECT count(*) FROM commercial_reservation.capacity_allocation WHERE tenant_id = ? AND reservation_id = ? AND salida_id = ? AND allocation_kind = 'RESERVATION' AND released_at IS NULL",
                tenant, s.reservation, s.proposed);
        int holds = count("SELECT count(*) FROM commercial_reservation.capacity_allocation WHERE tenant_id = ? AND reservation_id = ? AND allocation_kind = 'REPROGRAMMING_HOLD' AND released_at IS NULL", tenant, s.reservation);
        int records = count("SELECT count(*) FROM commercial_reservation.rescheduling_record WHERE tenant_id = ? AND rescheduling_episode_id = ?", tenant, s.episode);
        int pending = count("SELECT count(*) FROM commercial_reservation.rescheduling_proposal WHERE tenant_id = ? AND rescheduling_episode_id = ? AND proposal_state = 'PENDING'", tenant, s.episode);
        String proposalState = jdbc.queryForObject("SELECT proposal_state FROM commercial_reservation.rescheduling_proposal WHERE tenant_id = ? AND rescheduling_proposal_id = ?", String.class, tenant, s.proposal);
        boolean accepted = outcome == Outcome.ACCEPTED;
        boolean cancelled = outcome == Outcome.REJECTED || outcome == Outcome.EXPIRED;
        assertThat(pending).as("the proposal is closed once").isZero();
        assertThat(proposalState).as("the state of the proposal").isEqualTo(switch (outcome) { case ACCEPTED -> "ACCEPTED"; case REJECTED -> "REJECTED"; case EXPIRED -> "EXPIRED"; case VOIDED -> "VOID"; });
        assertThat(records).as("one record of the rescheduling, only when accepted").isEqualTo(accepted ? 1 : 0);
        assertThat(events(s.reservation, "ReservationRescheduled")).as("ReservationRescheduled").isEqualTo(accepted ? 1 : 0);
        assertThat(events(s.reservation, "ReservationCancelled")).as("ReservationCancelled").isEqualTo(cancelled ? 1 : 0);
        assertThat(reservationState).as("the reservation").isEqualTo(cancelled ? "CANCELLED" : "PENDING_PAYMENT");
        assertThat(reservationDeparture).as("the departure of the reservation").isEqualTo(accepted ? s.proposed : departure);
        assertThat(seats).as("the seat on the proposed departure, only when accepted").isEqualTo(accepted ? 1 : 0);
        assertThat(holds).as("no hold left behind").isZero();
        assertThat(held).as("seats held on the proposed departure").isEqualTo(accepted ? 3 : 0);
        return outcome;
    }

    private void oneWinner(List<Result> results, int expectedWinners) {
        assertThat(results).as("no answer is a 500: every loser is refused as a transition").allMatch(r -> r.status() == 200 || r.status() == 201 || r.status() == 422);
        assertThat(results.stream().filter(Result::won).count()).as("one transition wins").isEqualTo(expectedWinners);
    }

    // AC-18.20: two answers of the customer, with two keys, at once.
    @Test void twoAnswersAtOnceLeaveOne() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Scenario s = plant();
            var results = race(List.of(() -> call(NOW, customerAnswer(s, "ACCEPT", "race-ans-a-" + s.episode)), () -> call(NOW, customerAnswer(s, "REJECT", "race-ans-b-" + s.episode)),
                    () -> call(NOW, outsideAnswer(s, "ACCEPT", "race-ans-c-" + s.episode)), () -> call(NOW, outsideAnswer(s, "REJECT", "race-ans-d-" + s.episode))));

            oneWinner(results, 1);
            assertThat(consistent(s)).isIn(Outcome.ACCEPTED, Outcome.REJECTED);
        }
    }

    // AC-18.20: an acceptance and an expiry of the same episode.
    @Test void anAnswerAndAnExpiryAtOnceLeaveOne() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Scenario s = plant();
            var results = race(List.of(() -> call(NOW, customerAnswer(s, "ACCEPT", "race-exp-a-" + s.episode)), () -> call(AFTER_THE_DEADLINE, expiry(s))));

            oneWinner(results, 1);
            assertThat(consistent(s)).isIn(Outcome.ACCEPTED, Outcome.EXPIRED);
        }
    }

    // AC-18.20: a rejection and an expiry of the same episode.
    @Test void aRejectionAndAnExpiryAtOnceLeaveOne() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Scenario s = plant();
            var results = race(List.of(() -> call(NOW, customerAnswer(s, "REJECT", "race-rej-a-" + s.episode)), () -> call(AFTER_THE_DEADLINE, expiry(s))));

            oneWinner(results, 1);
            assertThat(consistent(s)).isIn(Outcome.REJECTED, Outcome.EXPIRED);
        }
    }

    // AC-18.20: the cancellation of the proposed departure (the voiding) and an acceptance.
    @Test void aVoidingAndAnAnswerAtOnceLeaveOne() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Scenario s = plant();
            var results = race(List.of(() -> call(NOW, customerAnswer(s, "ACCEPT", "race-void-a-" + s.episode)), () -> call(NOW, voiding(s, "race-void-b-" + s.episode))));

            assertThat(results).allMatch(r -> r.status() == 200 || r.status() == 201 || r.status() == 422);
            boolean voided = results.get(1).status() == 200 && results.get(1).body().get("voidedProposals").asInt() == 1;
            assertThat(voided ^ results.get(0).won()).as("the voiding or the answer wins, never both and never none").isTrue();
            assertThat(consistent(s)).isIn(Outcome.ACCEPTED, Outcome.VOIDED);
        }
    }

    // AC-18.20: the voiding and an expiry, and the three-way of an answer, an expiry and a voiding.
    @Test void anAnswerAnExpiryAndAVoidingAtOnceLeaveOne() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Scenario s = plant();
            var results = race(List.of(() -> call(NOW, customerAnswer(s, "ACCEPT", "race-tri-a-" + s.episode)), () -> call(AFTER_THE_DEADLINE, expiry(s)), () -> call(NOW, voiding(s, "race-tri-b-" + s.episode))));

            assertThat(results).allMatch(r -> r.status() == 200 || r.status() == 201 || r.status() == 422);
            boolean voided = results.get(2).status() == 200 && results.get(2).body().get("voidedProposals").asInt() == 1;
            assertThat((voided ? 1 : 0) + (results.get(0).won() ? 1 : 0) + (results.get(1).won() ? 1 : 0)).as("exactly one transition wins").isEqualTo(1);
            consistent(s);
        }
    }
}
