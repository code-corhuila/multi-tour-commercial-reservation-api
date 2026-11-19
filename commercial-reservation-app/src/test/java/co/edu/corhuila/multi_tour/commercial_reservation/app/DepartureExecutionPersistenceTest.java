package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureExecutionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ChangeDepartureExecutionUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// G-API4-1 (RN-DEX-01 to RN-DEX-04) on the real schema with two tenants: the start and the end move the rows of the departure of the tenant only, a repeated key
// answers the stored counts, and a departure of another tenant is not found. All values are invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DepartureExecutionPersistenceTest {
    private static final String KEY = "execution-key-0001";

    private ReservationPersistenceFixture mine, theirs;
    private JdbcReservationRepository repository;
    private ChangeDepartureExecutionUseCase useCase;
    private UUID paid, notPaid, theirPaid;

    @BeforeEach void plant() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        mine = new ReservationPersistenceFixture();
        theirs = new ReservationPersistenceFixture();
        for (var fixture : new ReservationPersistenceFixture[] {mine, theirs}) {
            new JdbcDepartureStateRepository(fixture.jdbc).save(new DepartureState(fixture.salida, fixture.tenant, fixture.service,
                    Instant.now().truncatedTo(ChronoUnit.MICROS).plus(3, ChronoUnit.DAYS), DepartureStatus.SCHEDULED, 10, 0));
        }
        repository = new JdbcReservationRepository(mine.jdbc, mine.cipher, mine.transactions, new JdbcReservationCreatedOutbox(mine.jdbc));
        paid = save(mine, true);
        notPaid = save(mine, false);
        theirPaid = save(theirs, true);
        PaymentTransaction transaction = new PaymentTransaction() {
            @Override public <T> T inTransaction(Supplier<T> work) { return mine.transactions.execute(status -> work.get()); }
        };
        useCase = new ChangeDepartureExecutionUseCase(new JdbcDepartureExecutionReader(mine.jdbc), repository, new JdbcIdempotencyStore(new JdbcIdempotencyKeys(mine.jdbc)),
                transaction);
    }

    private UUID save(ReservationPersistenceFixture fixture, boolean fullyPaid) {
        UUID id = UUID.randomUUID();
        var repo = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        Reservation reservation = fixture.reservation(id, true);
        repo.save(reservation);
        if (fullyPaid) {
            reservation.registerCash(UUID.randomUUID(), reservation.finalValue(), new Cents(1), fixture.actor, ReservationPersistenceFixture.NOW);
            repo.save(reservation);
        }
        return id;
    }

    private String statusOf(ReservationPersistenceFixture fixture, UUID id) {
        return fixture.jdbc.queryForObject("SELECT reservation_status FROM commercial_reservation.reservation WHERE tenant_id = ? AND reservation_id = ?", String.class,
                fixture.tenant, id);
    }

    @Test void theStartMovesTheFullyPaidReservationOfTheDepartureOfTheTenantOnly() {
        var done = useCase.start(mine.tenant, mine.salida, KEY);
        assertThat(done.changed()).isEqualTo(1);
        assertThat(done.unchanged()).isEqualTo(1);
        assertThat(statusOf(mine, paid)).isEqualTo("IN_EXECUTION");
        assertThat(statusOf(mine, notPaid)).isEqualTo("PENDING_PAYMENT");
        assertThat(statusOf(theirs, theirPaid)).isEqualTo("CONFIRMED");
    }

    @Test void theEndCompletesTheOnesInExecutionAndARepeatedKeyAnswersTheStoredCounts() {
        useCase.start(mine.tenant, mine.salida, KEY);
        var done = useCase.end(mine.tenant, mine.salida, "execution-key-0002");
        assertThat(done.changed()).isEqualTo(1);
        assertThat(statusOf(mine, paid)).isEqualTo("COMPLETED");
        assertThat(useCase.end(mine.tenant, mine.salida, "execution-key-0002")).isEqualTo(done);
        assertThat(statusOf(mine, paid)).isEqualTo("COMPLETED");
    }

    @Test void aDepartureOfAnotherTenantIsNotFoundAndLeavesNoKey() {
        assertThatThrownBy(() -> useCase.start(mine.tenant, theirs.salida, KEY)).isInstanceOf(DepartureNotFoundException.class);
        assertThat(useCase.start(mine.tenant, mine.salida, KEY).changed()).isEqualTo(1);
    }
}
