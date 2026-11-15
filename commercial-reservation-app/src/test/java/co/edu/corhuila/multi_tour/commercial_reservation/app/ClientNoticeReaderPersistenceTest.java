package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.StoredNotice;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-015 api T-05 over the real schema: the reader gives the notice of a tenant by its event id, with the tour, the amount of the fact and the
// balance; another tenant and an event that is not a notice are not found (AC-15.12). Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ClientNoticeReaderPersistenceTest {
    private static final Cents MINIMUM = new Cents(97_200);
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository repository;
    private JdbcPaymentEventOutbox events;
    private JdbcPaymentTransaction transaction;
    private JdbcClientNoticeReader reader;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        repository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        events = new JdbcPaymentEventOutbox(fixture.jdbc, new JdbcClientNoticeOutbox(fixture.jdbc));
        transaction = new JdbcPaymentTransaction(fixture.transactions);
        reader = new JdbcClientNoticeReader(fixture.jdbc);
    }

    private void persist(Reservation reservation) {
        transaction.inTransaction(() -> {
            repository.save(reservation);
            reservation.pullEvents().forEach(events::append);
            return null;
        });
    }

    private UUID approvedReservation() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));
        Reservation reservation = repository.findByTenantAndId(fixture.tenant, id).orElseThrow();
        UUID attempt = UUID.randomUUID();
        reservation.registerTransfer(attempt, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        reservation.allocateSeat(ReservationPersistenceFixture.NOW);
        persist(reservation);
        Reservation loaded = repository.findByTenantAndId(fixture.tenant, id).orElseThrow();
        loaded.approve(attempt, new Cents(100_000), null, MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(loaded);
        return id;
    }

    private UUID noticeOf(UUID reservation, String type) {
        return fixture.jdbc.queryForObject("SELECT outbox_event_id FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'ClientNotified' AND payload ->> 'notificationType' = ?", UUID.class, reservation, type);
    }

    @Test
    void theReaderGivesTheNoticeWithTheTourTheAmountAndTheBalanceOfItsFact() {
        UUID reservation = approvedReservation();

        StoredNotice approved = reader.find(fixture.tenant, noticeOf(reservation, "PROOF_APPROVED")).orElseThrow();

        assertThat(approved.type()).isEqualTo(NoticeType.PROOF_APPROVED);
        assertThat(approved.reservationId()).isEqualTo(reservation);
        assertThat(approved.customerId()).isEqualTo(fixture.customer);
        assertThat(approved.tourName()).isEqualTo("Servicio Ficticio");
        assertThat(approved.amountCents()).isEqualTo(100_000L);
        assertThat(approved.pendingBalanceCents()).isEqualTo(224_000L);
        assertThat(approved.causeCode()).isNull();
    }

    @Test
    void aNoticeOfAnotherTenantAndAnEventThatIsNotANoticeAreNotFound() {
        UUID reservation = approvedReservation();
        UUID notice = noticeOf(reservation, "RESERVATION_CONFIRMED");
        List<UUID> facts = fixture.jdbc.queryForList("SELECT outbox_event_id FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'PaymentValidated'", UUID.class, reservation);

        assertThat(reader.find(fixture.tenant, notice)).isPresent();
        assertThat(reader.find(UUID.randomUUID(), notice)).isEmpty();
        assertThat(facts).hasSize(1);
        assertThat(reader.find(fixture.tenant, facts.get(0))).isEmpty();
        assertThat(reader.find(fixture.tenant, UUID.randomUUID())).isEmpty();
    }
}
