package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-015 api T-02 over the real schema: the notice is one more row of the outbox, written in the transaction of its fact, with the four keys
// and no personal datum, once per fact (AC-15.1, AC-15.2, AC-15.8, AC-15.10). Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ClientNoticePersistenceTest {
    private static final Cents MINIMUM = new Cents(97_200);
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository repository;
    private JdbcPaymentEventOutbox events;
    private JdbcPaymentTransaction transaction;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        repository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        events = new JdbcPaymentEventOutbox(fixture.jdbc, new JdbcClientNoticeOutbox(fixture.jdbc));
        transaction = new JdbcPaymentTransaction(fixture.transactions);
    }

    private Reservation stored() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));
        return repository.findByTenantAndId(fixture.tenant, id).orElseThrow();
    }

    private void persist(Reservation reservation) {
        transaction.inTransaction(() -> {
            repository.save(reservation);
            reservation.pullEvents().forEach(events::append);
            return null;
        });
    }

    private List<Map<String, Object>> notices(Reservation reservation) {
        return fixture.jdbc.queryForList("SELECT outbox_event_id, tenant_id, customer_id, actor_id, payload::text AS payload FROM commercial_reservation.outbox_event "
                + "WHERE reservation_id = ? AND event_type = 'ClientNotified' ORDER BY occurred_at, payload::text", reservation.reservationId());
    }

    private List<String> noticeTypes(Reservation reservation) {
        return fixture.jdbc.queryForList("SELECT payload ->> 'notificationType' FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'ClientNotified' ORDER BY 1", String.class, reservation.reservationId());
    }

    private Reservation withApprovedTransfer() {
        Reservation reservation = stored();
        UUID attempt = UUID.randomUUID();
        reservation.registerTransfer(attempt, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        reservation.allocateSeat(ReservationPersistenceFixture.NOW);
        persist(reservation);
        Reservation loaded = repository.findByTenantAndId(fixture.tenant, reservation.reservationId()).orElseThrow();
        loaded.approve(attempt, new Cents(100_000), null, MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(loaded);
        return reservation;
    }

    @Test
    void approvalAndRejectionWriteOneNoticeInTheSameTransaction() {
        Reservation approved = withApprovedTransfer();
        assertThat(noticeTypes(approved)).containsExactly("PROOF_APPROVED", "RESERVATION_CONFIRMED");

        Reservation rejected = stored();
        UUID proof = UUID.randomUUID();
        rejected.registerTransfer(proof, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(rejected);
        Reservation loaded = repository.findByTenantAndId(fixture.tenant, rejected.reservationId()).orElseThrow();
        loaded.reject(proof, "The proof is illegible", fixture.actor, ReservationPersistenceFixture.NOW);
        persist(loaded);
        assertThat(noticeTypes(rejected)).containsExactly("PROOF_REJECTED");
    }

    @Test
    void aFactThatRollsBackLeavesNoNotice() {
        Reservation reservation = stored();
        UUID proof = UUID.randomUUID();
        reservation.registerTransfer(proof, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(reservation);
        Reservation loaded = repository.findByTenantAndId(fixture.tenant, reservation.reservationId()).orElseThrow();
        loaded.reject(proof, "The proof is illegible", fixture.actor, ReservationPersistenceFixture.NOW);

        assertThatThrownBy(() -> transaction.inTransaction(() -> {
            repository.save(loaded);
            loaded.pullEvents().forEach(events::append);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(noticeTypes(reservation)).isEmpty();
    }

    @Test
    void aCashPaymentRecordedByStaffWritesNoProofNotice() {
        Reservation reservation = stored();
        reservation.registerCash(UUID.randomUUID(), new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(reservation);

        assertThat(noticeTypes(reservation)).doesNotContain("PROOF_APPROVED");
    }

    @Test
    void theNoticePayloadHoldsOnlyTheFourKeysAndNoPersonalDatum() {
        Reservation reservation = withApprovedTransfer();

        List<Map<String, Object>> rows = notices(reservation);
        assertThat(rows).hasSize(2);
        for (Map<String, Object> row : rows) {
            String payload = (String) row.get("payload");
            assertThat(payload).contains("\"customerId\"", "\"notificationType\"", "\"triggeringEventType\"", "\"triggeringEventId\"");
            assertThat(fixture.jdbc.queryForObject("SELECT (SELECT count(*) FROM jsonb_object_keys(?::jsonb))", Integer.class, payload)).isEqualTo(4);
            assertThat(payload).doesNotContain("@").doesNotContain(ReservationPersistenceFixture.COMPANION_NAME);
            assertThat(row.get("tenant_id")).isEqualTo(fixture.tenant);
            assertThat(row.get("customer_id")).isNull();
        }
        assertThat(rows.stream().map(row -> (String) row.get("payload")).anyMatch(payload -> payload.contains("\"customerId\": \"" + fixture.customer + "\""))).isTrue();
    }

    @Test
    void theNoticeNamesTheEventThatTriggeredItAndOnlyOnce() {
        Reservation reservation = withApprovedTransfer();

        List<String> triggering = fixture.jdbc.queryForList("SELECT payload ->> 'triggeringEventId' FROM commercial_reservation.outbox_event "
                + "WHERE reservation_id = ? AND event_type = 'ClientNotified'", String.class, reservation.reservationId());
        assertThat(triggering).doesNotHaveDuplicates().hasSize(2);
        for (String id : triggering) {
            assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE outbox_event_id = ?::uuid AND reservation_id = ? "
                    + "AND event_type IN ('PaymentValidated', 'ReservationConfirmed')", Integer.class, id, reservation.reservationId())).isEqualTo(1);
        }
    }

    @Test
    void theSameFactTwiceLeavesOneNotice() {
        Reservation reservation = withApprovedTransfer();
        UUID triggering = fixture.jdbc.queryForObject("SELECT outbox_event_id FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'PaymentValidated'", UUID.class, reservation.reservationId());
        var notices = new JdbcClientNoticeOutbox(fixture.jdbc);

        transaction.inTransaction(() -> {
            notices.append(fixture.tenant, reservation.reservationId(),
                    co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType.PROOF_APPROVED, "PaymentValidated", triggering,
                    ReservationPersistenceFixture.NOW);
            return null;
        });

        assertThat(noticeTypes(reservation)).containsExactly("PROOF_APPROVED", "RESERVATION_CONFIRMED");
    }
}
