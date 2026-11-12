package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingReads;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-018 AC-18.16, AC-18.18 over the real schema: the reads of the episodes join the reservation for the customer, always lead with the tenant, and filter by reservation, original
// departure and state. A customer that is not the holder, and a tenant that is not the owner, find nothing. Invented values.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReschedulingReadsPersistenceTest {
    private ReservationPersistenceFixture a;
    private JdbcReschedulingRepository episodes;
    private JdbcReschedulingReads reads;
    private UUID reservation;
    private UUID episode;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        a = new ReservationPersistenceFixture();
        episodes = new JdbcReschedulingRepository(a.jdbc);
        reads = new JdbcReschedulingReads(a.jdbc, episodes);
        reservation = UUID.randomUUID();
        var held = a.reservation(reservation, true);
        new JdbcReservationRepository(a.jdbc, a.cipher, a.transactions, new JdbcReservationCreatedOutbox(a.jdbc)).save(held);
        episode = UUID.randomUUID();
        episodes.save(ReschedulingEpisode.open(episode, a.tenant, reservation, held.departureId(), ReservationPersistenceFixture.NOW, ReservationPersistenceFixture.NOW));
    }

    @Test
    void theHolderAndTheStaffFindTheEpisodeAndAnotherCustomerOrTenantFindsNothing() {
        assertThat(reads.find(a.tenant, null, episode)).isPresent().get().extracting(view -> view.customerId()).isEqualTo(a.customer);
        assertThat(reads.find(a.tenant, a.customer, episode)).isPresent();
        assertThat(reads.find(a.tenant, UUID.randomUUID(), episode)).isEmpty();
        assertThat(reads.find(UUID.randomUUID(), null, episode)).isEmpty();
        assertThat(reads.page(a.tenant, UUID.randomUUID(), null, null, null, 1, 20).items()).isEmpty();
        assertThat(reads.page(UUID.randomUUID(), null, null, null, null, 1, 20).total()).isZero();
    }

    @Test
    void thePageNarrowsByReservationDepartureAndStateAndCountsTheWholeAnswer() {
        assertThat(reads.page(a.tenant, a.customer, reservation, null, EpisodeState.OPEN, 1, 20).total()).isEqualTo(1);
        assertThat(reads.page(a.tenant, null, UUID.randomUUID(), null, null, 1, 20).items()).isEmpty();
        assertThat(reads.page(a.tenant, null, null, UUID.randomUUID(), null, 1, 20).items()).isEmpty();
        assertThat(reads.page(a.tenant, null, null, null, EpisodeState.CANCELLED, 1, 20).items()).isEmpty();
        assertThat(reads.page(a.tenant, null, null, null, null, 2, 1).items()).as("the second page of one is empty").isEmpty();
        assertThat(reads.page(a.tenant, null, null, null, null, 2, 1).total()).isEqualTo(1);
    }
}
