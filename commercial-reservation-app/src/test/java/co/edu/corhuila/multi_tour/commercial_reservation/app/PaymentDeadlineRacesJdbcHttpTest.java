package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExpiryResult;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ExpireReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-011 over the real database with every real JDBC bean (audit 2026-11-07): the races the unit fakes cannot show. Two requests
// start together on a latch and, in every round, the outcome must be one of the two consistent orders (never a 500, never a lost
// update, one cancellation event at most). The expiry uses a later clock than the HTTP side, so the reservation is due for the job and
// still open for the request. The seat release of the expiry of a confirmed reservation (case BALANCE) is read from the capacity row,
// the allocation and the outbox. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
@Import(PaymentJdbcHttpTest.FixedClock.class)
class PaymentDeadlineRacesJdbcHttpTest {
    static final String BASE = PaymentJdbcHttpTest.BASE;
    static final int ROUNDS = 5;
    // The initial deadline of a reservation created at NOW is NOW + 24 h (initialWaitHours of the seeded condition).
    static final Instant PAST_THE_DEADLINE = TestJwtKeys.NOW.plus(Duration.ofHours(25));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogServiceRepository catalog;
    @Autowired CustomerRepository customers;
    @Autowired ReservationRepository reservations;
    @Autowired PaymentDeadlineReader deadlines;
    @Autowired DepartureReader departures;
    @Autowired SalidaCapacityHolder holder;
    @Autowired CancelledValueRecorder cancelledValues;
    @Autowired PaymentEventOutbox outbox;
    @Autowired PaymentTransaction transaction;

    final UUID tenant = UUID.randomUUID(), tour = UUID.randomUUID(), departure = UUID.randomUUID(), customer = UUID.randomUUID();
    int keys;

    @BeforeEach void seed() throws Exception {
        OffsetDateTime now = OffsetDateTime.ofInstant(TestJwtKeys.NOW, ZoneOffset.UTC);
        jdbc.update("INSERT INTO commercial_reservation.tenant_status (tenant_id, identifier, status, time_zone, country, "
                + "last_event_occurred_at, created_at, updated_at) VALUES (?, ?, 'ACTIVE', 'America/Bogota', 'CO', ?, ?, ?)",
                tenant, "t-" + tenant, now, now, now);
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio",
                CommercialTerms.of(100_000L, 40_000L, 100, false, false), TestJwtKeys.NOW));
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, "
                + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 100, 4)",
                UUID.randomUUID(), tenant, departure, tour, now.plusDays(10));
        customers.save(Customer.registerLead(customer, tenant, "CC12345", "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("persona@example.invalid", "3000000000"), true, TestJwtKeys.NOW));
        var row = Map.of("paymentType", "DEPOSIT", "initialWaitHours", 24, "additionalWaitHours", 48, "minimumDepositBp", 3000,
                "transferEnabled", true, "cashEnabled", true);
        send(put(BASE + "/payment-parameters").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("reason", "Opening season", "parameters", List.of(row)))));
    }

    String key() { return "races-" + tenant.toString().substring(0, 8) + "-" + (++keys) + "-" + UUID.randomUUID().toString().substring(0, 8); }

    int send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + CatalogHttpTest.token(tenant.toString(), "TENANT_ADMINISTRATOR"))
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn().getResponse().getStatus();
    }

    UUID reservation() throws Exception {
        var body = Map.of("serviceId", tour.toString(), "salidaId", departure.toString(), "people", 3, "customerId", customer.toString(),
                "paymentType", "DEPOSIT", "paymentMethod", "TRANSFER");
        var result = mvc.perform(post(BASE + "/reservations").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body)).header("Authorization", "Bearer "
                        + CatalogHttpTest.token(tenant.toString(), "TENANT_ADMINISTRATOR")).header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(CatalogHttpTest.json(result).get("reservationId").asText());
    }

    int extension(UUID reservation, int hours) throws Exception {
        return send(post(BASE + "/reservations/" + reservation + "/payment-deadline/extension").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("hours", hours))));
    }

    int transfer(UUID reservation, long amount) throws Exception {
        return send(multipart(BASE + "/reservations/" + reservation + "/payments")
                .file(new MockMultipartFile("payment", "", "application/json",
                        TestJwtKeys.JSON.writeValueAsBytes(Map.of("paymentMethod", "TRANSFER", "amountCents", amount))))
                .file(new MockMultipartFile("proof", "proof.png", "image/png", PaymentJdbcHttpTest.PNG)).header("Idempotency-Key", key()));
    }

    ExpiryResult expire(UUID reservation, Instant at) {
        return new ExpireReservationUseCase(reservations, deadlines, departures, holder, cancelledValues, outbox, transaction,
                Clock.fixed(at, ZoneOffset.UTC)).expire(tenant, reservation);
    }

    // Both callables wait on one latch and then run together; a failure of either one is rethrown, so a 500 or an exception fails the round.
    <A, B> List<Object> together(Callable<A> first, Callable<B> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> work : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return work.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Object> answers = new ArrayList<>();
            for (Future<Object> future : futures) answers.add(future.get(60, TimeUnit.SECONDS));
            return answers;
        } finally {
            pool.shutdownNow();
        }
    }

    String status(UUID reservation) {
        return jdbc.queryForObject("SELECT reservation_status FROM commercial_reservation.reservation WHERE reservation_id = ?", String.class, reservation);
    }

    int events(UUID reservation, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE reservation_id = ? AND event_type = ?",
                Integer.class, reservation, type);
    }

    Integer extensionHours(UUID reservation) {
        return jdbc.queryForObject("SELECT extension_hours FROM commercial_reservation.payment_deadline WHERE reservation_id = ?", Integer.class, reservation);
    }

    int heldSeats() {
        return jdbc.queryForObject("SELECT held_seats FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?",
                Integer.class, tenant, departure);
    }

    @Test void twoSimultaneousExtensionsOfTheSameDeadlineGrantExactlyOneAndRefuseTheOther() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID id = reservation();

            List<Object> answers = together(() -> extension(id, 5), () -> extension(id, 7));

            assertThat(answers).as("round " + round).containsExactlyInAnyOrder(201, 422);
            assertThat(extensionHours(id)).as("round " + round).isIn(5, 7);
            assertThat(status(id)).isEqualTo("PENDING_PAYMENT");
        }
    }

    @Test void anExtensionRacingWithTheExpiryEndsInOneConsistentOrder() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID id = reservation();

            List<Object> answers = together(() -> extension(id, 5), () -> expire(id, PAST_THE_DEADLINE));

            int extension = (Integer) answers.get(0);
            ExpiryResult expiry = (ExpiryResult) answers.get(1);
            if (extension == 201) {
                // The extension won the row: the job, with its later clock, finds the deadline moved and leaves the reservation open.
                assertThat(expiry.expired()).as("round " + round).isFalse();
                assertThat(status(id)).isEqualTo("PENDING_PAYMENT");
                assertThat(extensionHours(id)).isEqualTo(5);
                assertThat(events(id, "ReservationCancelled")).isZero();
            } else {
                // The expiry won the row: the reservation is cancelled once and the extension is refused, not lost or applied.
                assertThat(extension).as("round " + round).isEqualTo(422);
                assertThat(expiry.expired()).isTrue();
                assertThat(status(id)).isEqualTo("CANCELLED");
                assertThat(extensionHours(id)).isNull();
                assertThat(events(id, "ReservationCancelled")).isEqualTo(1);
            }
        }
    }

    @Test void anExpiryRacingWithAPaymentRegistrationEndsInOneConsistentOrder() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID id = reservation();
            int heldBefore = heldSeats();

            List<Object> answers = together(() -> transfer(id, 90_000), () -> expire(id, PAST_THE_DEADLINE));

            int payment = (Integer) answers.get(0);
            ExpiryResult expiry = (ExpiryResult) answers.get(1);
            int attempts = jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.payment_attempt WHERE reservation_id = ?", Integer.class, id);
            if (payment == 201) {
                // The proof was recorded in time before the job looked: the evidence prevails and the reservation stays open with its seat.
                assertThat(expiry.expired()).as("round " + round).isFalse();
                assertThat(status(id)).isEqualTo("PENDING_PAYMENT");
                assertThat(attempts).isEqualTo(1);
                assertThat(events(id, "ReservationCancelled")).isZero();
                assertThat(heldSeats()).isEqualTo(heldBefore + 3);
            } else {
                assertThat(payment).as("round " + round).isEqualTo(422);
                assertThat(expiry.expired()).isTrue();
                assertThat(status(id)).isEqualTo("CANCELLED");
                assertThat(attempts).isZero();
                assertThat(events(id, "ReservationCancelled")).isEqualTo(1);
                assertThat(heldSeats()).isEqualTo(heldBefore);
            }
        }
    }

    // Case BALANCE: a confirmed reservation with a held seat and a balance deadline that was reached is cancelled by the job, the seat goes
    // back to the capacity row once, and exactly one CapacityReleased and one ReservationCancelled are written.
    @Test void theExpiryOfAConfirmedReservationWithAHeldSeatReleasesItOnceAndWritesItsTwoEvents() throws Exception {
        UUID id = reservation();
        assertThat(transfer(id, 90_000)).isEqualTo(201);
        UUID attempt = jdbc.queryForObject("SELECT payment_attempt_id FROM commercial_reservation.payment_attempt WHERE reservation_id = ?", UUID.class, id);
        assertThat(send(post(BASE + "/payment-proofs/" + attempt + "/approve").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("verifiedAmountCents", 90_000L))))).isEqualTo(200);
        assertThat(status(id)).isEqualTo("CONFIRMED");
        assertThat(heldSeats()).isEqualTo(7);
        jdbc.update("INSERT INTO commercial_reservation.payment_deadline (payment_deadline_id, tenant_id, reservation_id, deadline_kind, due_at) "
                + "VALUES (?, ?, ?, 'BALANCE', ?)", UUID.randomUUID(), tenant, id, OffsetDateTime.ofInstant(TestJwtKeys.NOW.plus(Duration.ofHours(30)), ZoneOffset.UTC));

        ExpiryResult first = expire(id, TestJwtKeys.NOW.plus(Duration.ofHours(31)));
        ExpiryResult second = expire(id, TestJwtKeys.NOW.plus(Duration.ofHours(32)));

        assertThat(first.expired()).isTrue();
        assertThat(second.expired()).isFalse();
        assertThat(status(id)).isEqualTo("CANCELLED");
        assertThat(heldSeats()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.capacity_allocation WHERE reservation_id = ? AND released_at IS NULL",
                Integer.class, id)).isZero();
        assertThat(events(id, "CapacityReleased")).isEqualTo(1);
        assertThat(events(id, "ReservationCancelled")).isEqualTo(1);
    }

    // Audit finding 2026-11-07: the due list reads every deadline row, the expiry reads the row with the latest due instant. Whatever the list
    // offers to the job must be expirable, or the reservation comes back every minute as NOT_DUE.
    @Test void aReservationOfferedByTheDueListIsExpirableWhenItHasABalanceAndALaterPendingDifferenceDeadline() throws Exception {
        UUID id = reservation();
        assertThat(transfer(id, 90_000)).isEqualTo(201);
        UUID attempt = jdbc.queryForObject("SELECT payment_attempt_id FROM commercial_reservation.payment_attempt WHERE reservation_id = ?", UUID.class, id);
        assertThat(send(post(BASE + "/payment-proofs/" + attempt + "/approve").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("verifiedAmountCents", 90_000L))))).isEqualTo(200);
        for (var row : List.of(List.of("BALANCE", 30), List.of("PENDING_DIFFERENCE", 60))) {
            jdbc.update("INSERT INTO commercial_reservation.payment_deadline (payment_deadline_id, tenant_id, reservation_id, deadline_kind, due_at) "
                    + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), tenant, id, row.get(0),
                    OffsetDateTime.ofInstant(TestJwtKeys.NOW.plus(Duration.ofHours((Integer) row.get(1))), ZoneOffset.UTC));
        }
        Instant at = TestJwtKeys.NOW.plus(Duration.ofHours(31));

        var listed = new co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDuePaymentQueries(jdbc).page(tenant, at, 1, 100);
        boolean offered = listed.items().stream().anyMatch(view -> view.reservationId().equals(id));
        ExpiryResult answer = expire(id, at);

        assertThat(offered).as("the list offers the reservation").isTrue();
        assertThat(answer.expired()).as("the expiry answered " + answer.reason()).isTrue();
    }
}
