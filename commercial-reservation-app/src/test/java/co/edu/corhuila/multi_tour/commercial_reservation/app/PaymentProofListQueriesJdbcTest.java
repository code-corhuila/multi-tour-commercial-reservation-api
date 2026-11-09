package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSource;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// HU-COM-010 performance debt of the audit of 2026-11-06: the review list read one reservation (several statements) per proof. Over the
// real schema, the statements a page costs must not grow with the number of proofs on it, and the page must still carry the same
// reservation data (state, balance, companions, discount lines, every attempt) as the single read. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PaymentProofListQueriesJdbcTest {
    private static final Cents MINIMUM = new Cents(97_200);
    private final AtomicInteger statements = new AtomicInteger();
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository reservations;
    private JdbcPaymentProofReader reader;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        OffsetDateTime start = OffsetDateTime.ofInstant(ReservationPersistenceFixture.NOW, ZoneOffset.UTC).plusDays(5);
        fixture.jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, "
                + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 100, 0)", UUID.randomUUID(),
                fixture.tenant, fixture.salida, fixture.service, start);
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"), System.getenv("TEST_DATABASE_USER"),
                System.getenv("TEST_DATABASE_PASSWORD"));
        var counting = new JdbcTemplate(countingStatementsOf(source));
        reservations = new JdbcReservationRepository(counting, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(counting));
        reader = new JdbcPaymentProofReader(counting, reservations);
    }

    // Every statement prepared on a connection of this source is one round trip to the database, so it is the thing to count.
    private DataSource countingStatementsOf(DriverManagerDataSource source) {
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
            Object result = invoke(method, source, args);
            if (!method.getName().equals("getConnection")) return result;
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class}, (conn, call, callArgs) -> {
                if (call.getName().equals("prepareStatement") || call.getName().equals("createStatement")) statements.incrementAndGet();
                return invoke(call, result, callArgs);
            });
        });
    }

    private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    // One reservation per proof, each with two companions (one with sealed high-risk data), at one minute from the previous one.
    private List<UUID> proofsInValidation(int reservationCount, Instant first) {
        List<UUID> attempts = new ArrayList<>();
        for (int i = 0; i < reservationCount; i++) {
            UUID id = UUID.randomUUID(), attempt = UUID.randomUUID();
            Instant at = first.plusSeconds(60L * i);
            reservations.save(fixture.reservation(id, true, fixture.plainCompanion(), fixture.riskyCompanion()));
            Reservation reservation = reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
            reservation.registerTransfer(attempt, new Cents(100_000 + i), MINIMUM, fixture.actor, at);
            reservation.allocateSeat(at);
            reservations.save(reservation);
            attempts.add(attempt);
        }
        return attempts;
    }

    private int statementsOfOnePage() {
        statements.set(0);
        var page = reader.find(fixture.tenant, ValidationStatus.IN_VALIDATION, null, 1, 100);
        assertThat(page.items()).isNotEmpty();
        return statements.get();
    }

    @Test
    void thePageCostsTheSameNumberOfStatementsWhateverTheNumberOfProofs() {
        proofsInValidation(2, ReservationPersistenceFixture.NOW);
        int withTwo = statementsOfOnePage();
        proofsInValidation(8, ReservationPersistenceFixture.NOW.plusSeconds(3600));
        int withTen = statementsOfOnePage();

        assertThat(withTen).as("statements of a page of 10 proofs against a page of 2").isEqualTo(withTwo);
        assertThat(withTen).as("the list query, and a fixed number of batch reads of the reservations").isLessThanOrEqualTo(10);
    }

    @Test
    void theBatchPageCarriesTheSameReservationDataAsTheSingleRead() {
        List<UUID> attempts = proofsInValidation(3, ReservationPersistenceFixture.NOW);

        var page = reader.find(fixture.tenant, ValidationStatus.IN_VALIDATION, null, 1, 100);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(item -> item.attempt().attemptId()).containsExactlyElementsOf(attempts);
        for (ProofSource item : page.items()) {
            Reservation single = reservations.findByTenantAndId(fixture.tenant, item.reservation().reservationId()).orElseThrow();
            assertThat(item.reservation().reservationStatus()).isEqualTo(single.reservationStatus());
            assertThat(item.reservation().paymentStatus()).isEqualTo(single.paymentStatus());
            assertThat(item.reservation().pendingBalance()).isEqualTo(single.pendingBalance());
            assertThat(item.reservation().customerId()).isEqualTo(single.customerId());
            assertThat(item.reservation().seatHeld()).isEqualTo(single.seatHeld()).isTrue();
            assertThat(item.reservation().companions()).hasSize(2).usingRecursiveComparison().isEqualTo(single.companions());
            assertThat(item.reservation().fixedDiscounts()).hasSize(2).usingRecursiveComparison().isEqualTo(single.fixedDiscounts());
            assertThat(item.reservation().attempts()).hasSize(1).usingRecursiveComparison().isEqualTo(single.attempts());
        }
    }

    @Test
    void aReservationWithTwoProofsOnThePageKeepsBothAttempts() {
        UUID id = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        reservations.save(fixture.reservation(id, true));
        Reservation reservation = reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
        reservation.registerTransfer(first, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        reservation.registerTransfer(second, new Cents(100_001), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW.plusSeconds(60));
        reservation.allocateSeat(ReservationPersistenceFixture.NOW);
        reservations.save(reservation);

        var page = reader.find(fixture.tenant, ValidationStatus.IN_VALIDATION, null, 1, 100);

        assertThat(page.items()).extracting(item -> item.attempt().attemptId()).containsExactly(first, second);
        assertThat(page.items()).allSatisfy(item -> assertThat(item.reservation().attempts()).hasSize(2));
    }

    @Test
    void anotherTenantsProofsNeverReachThePage() {
        proofsInValidation(2, ReservationPersistenceFixture.NOW);
        var other = new ReservationPersistenceFixture();

        assertThat(reader.find(other.tenant, ValidationStatus.IN_VALIDATION, null, 1, 100).items()).isEmpty();
    }
}
