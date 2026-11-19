package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcInternalDepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.InternalDepartureReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// G-API6-1 (RN-IDR-01, RN-IDR-03, RN-IDR-05) on the real schema with two tenants: the tenant and the departure are conditions of the statements, so a departure
// of another tenant is the same empty answer as a missing one. All values are invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class InternalDepartureReservationQueriesPersistenceTest {
    private ReservationPersistenceFixture mine, theirs;
    private JdbcInternalDepartureReservationQueries queries;
    private UUID first, second;

    @BeforeEach void plant() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        mine = new ReservationPersistenceFixture();
        theirs = new ReservationPersistenceFixture();
        queries = new JdbcInternalDepartureReservationQueries(mine.jdbc);
        for (var fixture : new ReservationPersistenceFixture[] {mine, theirs}) {
            new JdbcDepartureStateRepository(fixture.jdbc).save(new DepartureState(fixture.salida, fixture.tenant, fixture.service,
                    Instant.now().truncatedTo(ChronoUnit.MICROS).plus(3, ChronoUnit.DAYS), DepartureStatus.SCHEDULED, 10, 0));
        }
        first = save(mine);
        second = save(mine);
        save(theirs);
    }

    private UUID save(ReservationPersistenceFixture fixture) {
        UUID id = UUID.randomUUID();
        new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc))
                .save(fixture.reservation(id, true));
        return id;
    }

    @Test void theDepartureAnswersItsReservationsWithTheStatusThePeopleTheCustomerIdAndTheValidatedAmount() {
        mine.jdbc.update("UPDATE commercial_reservation.reservation SET pending_balance_cents = pending_balance_cents - 1000 WHERE tenant_id = ? AND reservation_id = ?",
                mine.tenant, first);
        var page = queries.ofDeparture(mine.tenant, mine.salida, 1, 20).orElseThrow();
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(InternalDepartureReservation::reservationId).containsExactlyInAnyOrder(first, second);
        assertThat(page.items()).allSatisfy(row -> {
            assertThat(row.travelers()).isEqualTo(3);
            assertThat(row.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
            assertThat(row.customerId()).isEqualTo(mine.customer);
        });
        assertThat(page.items().stream().filter(row -> row.reservationId().equals(first)).findFirst().orElseThrow().validatedAmountCents()).isEqualTo(1000L);
        assertThat(page.items().stream().filter(row -> row.reservationId().equals(second)).findFirst().orElseThrow().validatedAmountCents()).isZero();
    }

    @Test void theAnswerIsPagedAndTheTotalCountsTheWholeDeparture() {
        var page = queries.ofDeparture(mine.tenant, mine.salida, 2, 1).orElseThrow();
        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(2);
    }

    @Test void aDepartureOfAnotherTenantOrAMissingOneIsEmpty() {
        assertThat(queries.ofDeparture(theirs.tenant, mine.salida, 1, 20)).isEmpty();
        assertThat(queries.ofDeparture(mine.tenant, theirs.salida, 1, 20)).isEmpty();
        assertThat(queries.ofDeparture(mine.tenant, UUID.randomUUID(), 1, 20)).isEmpty();
        assertThat(queries.ofDeparture(theirs.tenant, theirs.salida, 1, 20).orElseThrow().total()).isEqualTo(1);
    }

    @Test void aDepartureWithoutReservationsIsAnEmptyPage() {
        UUID empty = UUID.randomUUID();
        new JdbcDepartureStateRepository(mine.jdbc).save(new DepartureState(empty, mine.tenant, mine.service,
                Instant.now().truncatedTo(ChronoUnit.MICROS).plus(4, ChronoUnit.DAYS), DepartureStatus.SCHEDULED, 10, 0));
        var page = queries.ofDeparture(mine.tenant, empty, 1, 20).orElseThrow();
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }
}
