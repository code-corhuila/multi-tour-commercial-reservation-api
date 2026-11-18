package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// Spec 023 (-api), T-01: the keyset read of the alive reservations of a departure over the real schema, led by the tenant and ordered by id. A tenant that does not own the
// departure reads nothing, and a departure with no reservations is an empty page. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DepartureReservationReaderPersistenceTest {
    private ReservationPersistenceFixture fixture;
    private JdbcDepartureReservationReader reader;
    private List<UUID> ids;

    // PostgreSQL orders a uuid by its unsigned bytes, which equals the order of its lowercase text; UUID.compareTo is signed and differs.
    @BeforeEach
    void plant() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        reader = new JdbcDepartureReservationReader(fixture.jdbc);
        var reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        ids.forEach(id -> reservations.save(fixture.reservation(id, true)));
    }

    @Test
    void pagesTheAliveReservationsByKeysetInOrder() {
        List<UUID> first = reader.findAliveByDeparture(fixture.tenant, fixture.salida, null, 2);
        List<UUID> second = reader.findAliveByDeparture(fixture.tenant, fixture.salida, first.get(1), 2);

        assertThat(first).containsExactly(ids.get(0), ids.get(1));
        assertThat(second).containsExactly(ids.get(2));
    }

    @Test
    void aTenantThatDoesNotOwnTheDepartureReadsNothing() {
        assertThat(reader.findAliveByDeparture(UUID.randomUUID(), fixture.salida, null, 100)).isEmpty();
        assertThat(reader.findAliveByDeparture(fixture.tenant, UUID.randomUUID(), null, 100)).isEmpty();
    }
}
