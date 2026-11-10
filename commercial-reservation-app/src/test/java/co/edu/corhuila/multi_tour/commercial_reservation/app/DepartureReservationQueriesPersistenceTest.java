package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-012 amendment 2026-11-10, R2 (AC-12.R2.1 to AC-12.R2.3, AC-12.R2.5) on the real schema with two tenants: the tenant, the tour and the departure are
// conditions of the statements, so a departure of another tenant or another tour is the same empty answer as a missing one. All values are invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DepartureReservationQueriesPersistenceTest {
    private ReservationPersistenceFixture mine, theirs;
    private JdbcDepartureReservationQueries queries;
    private UUID first, second;

    @BeforeEach void plant() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        mine = new ReservationPersistenceFixture();
        theirs = new ReservationPersistenceFixture();
        queries = new JdbcDepartureReservationQueries(mine.jdbc);
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

    @Test void AC_12_R2_1_theDepartureAnswersItsReservationsWithTheNameThePeopleAndTheState() {
        var page = queries.ofDeparture(mine.tenant, mine.service, mine.salida, 1, 20).orElseThrow();
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(DepartureReservation::reservationId).containsExactlyInAnyOrder(first, second);
        assertThat(page.items()).allSatisfy(row -> {
            assertThat(row.customerName()).isEqualTo("Lead Ficticio");
            assertThat(row.people()).isEqualTo(3);
            assertThat(row.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        });
    }

    @Test void AC_12_R2_1_theAnswerIsPagedAndTheTotalCountsTheWholeDeparture() {
        var page = queries.ofDeparture(mine.tenant, mine.service, mine.salida, 2, 1).orElseThrow();
        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(2);
    }

    @Test void AC_12_R2_3_aDepartureOfAnotherTenantOrAnotherTourOrAMissingOneIsEmpty() {
        assertThat(queries.ofDeparture(theirs.tenant, mine.service, mine.salida, 1, 20)).isEmpty();
        assertThat(queries.ofDeparture(mine.tenant, theirs.service, theirs.salida, 1, 20)).isEmpty();
        assertThat(queries.ofDeparture(mine.tenant, theirs.service, mine.salida, 1, 20)).isEmpty();
        assertThat(queries.ofDeparture(mine.tenant, mine.service, UUID.randomUUID(), 1, 20)).isEmpty();
        assertThat(queries.ofDeparture(theirs.tenant, theirs.service, theirs.salida, 1, 20).orElseThrow().total()).isEqualTo(1);
    }

    @Test void AC_12_R2_2_aDepartureWithoutReservationsIsAnEmptyPage() {
        UUID empty = UUID.randomUUID();
        new JdbcDepartureStateRepository(mine.jdbc).save(new DepartureState(empty, mine.tenant, mine.service,
                Instant.now().truncatedTo(ChronoUnit.MICROS).plus(4, ChronoUnit.DAYS), DepartureStatus.SCHEDULED, 10, 0));
        var page = queries.ofDeparture(mine.tenant, mine.service, empty, 1, 20).orElseThrow();
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }
}
