package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcOutboxDeliveryStore;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RelayOutboxDeliveryUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-020 AC-20.13, RN-12, DP-05 (PROPOSED default) over the real schema (-db 045): an action of the restricted Administrator writes its events with the reason RESTRICTED_ACCESS in the envelope and no new key in the
// payload; the same action with an ordinary token leaves the reason empty; job 7 reads the reason with the rest of the envelope. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class RestrictedActionAuditPersistenceTest {
    private static final Cents MINIMUM = new Cents(97_200);
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository repository;
    private JdbcPaymentTransaction transaction;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        repository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        transaction = new JdbcPaymentTransaction(fixture.transactions);
    }

    private UUID approvedReservation(JdbcPaymentEventOutbox events) {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));
        Reservation reservation = repository.findByTenantAndId(fixture.tenant, id).orElseThrow();
        UUID attempt = UUID.randomUUID();
        reservation.registerTransfer(attempt, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        reservation.allocateSeat(ReservationPersistenceFixture.NOW);
        persist(reservation, events);
        Reservation loaded = repository.findByTenantAndId(fixture.tenant, id).orElseThrow();
        loaded.approve(attempt, new Cents(100_000), null, MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(loaded, events);
        return id;
    }

    private void persist(Reservation reservation, JdbcPaymentEventOutbox events) {
        transaction.inTransaction(() -> {
            repository.save(reservation);
            reservation.pullEvents().forEach(events::append);
            return null;
        });
    }

    private String reasonOf(UUID reservation, String eventType) {
        return fixture.jdbc.queryForObject("SELECT reason FROM commercial_reservation.outbox_event WHERE reservation_id = ? AND event_type = ?", String.class, reservation, eventType);
    }

    @Test
    void aRestrictedActionIsAuditedWithTheMarkerAndNoValue() {
        UUID reservation = approvedReservation(new JdbcPaymentEventOutbox(fixture.jdbc, new JdbcClientNoticeOutbox(fixture.jdbc), () -> Optional.of("RESTRICTED_ACCESS")));

        assertThat(reasonOf(reservation, "PaymentValidated")).isEqualTo("RESTRICTED_ACCESS");
        assertThat(fixture.jdbc.queryForObject("SELECT payload::text FROM commercial_reservation.outbox_event WHERE reservation_id = ? AND event_type = 'PaymentValidated'", String.class,
                reservation)).doesNotContain("RESTRICTED_ACCESS", "reason");
    }

    @Test
    void anOrdinaryActionLeavesTheReasonEmpty() {
        UUID reservation = approvedReservation(new JdbcPaymentEventOutbox(fixture.jdbc, new JdbcClientNoticeOutbox(fixture.jdbc)));

        assertThat(reasonOf(reservation, "PaymentValidated")).isNull();
    }

    @Test
    void theDeliveryReadCarriesTheReasonOfTheEvent() {
        UUID reservation = approvedReservation(new JdbcPaymentEventOutbox(fixture.jdbc, new JdbcClientNoticeOutbox(fixture.jdbc), () -> Optional.of("RESTRICTED_ACCESS")));
        UUID eventId = fixture.jdbc.queryForObject("SELECT outbox_event_id FROM commercial_reservation.outbox_event WHERE reservation_id = ? AND event_type = 'PaymentValidated'", UUID.class,
                reservation);
        RelayOutboxDeliveryUseCase relay = new RelayOutboxDeliveryUseCase(new JdbcOutboxDeliveryStore(fixture.jdbc), Clock.systemUTC());

        OutboxEventView seen = null;
        for (int page = 1; seen == null; page++) {
            var items = relay.pending(page, 100).items();
            if (items.isEmpty()) break;
            seen = items.stream().filter(view -> view.eventId().equals(eventId)).findFirst().orElse(null);
        }

        assertThat(seen).isNotNull();
        assertThat(seen.reason()).isEqualTo("RESTRICTED_ACCESS");
    }
}
