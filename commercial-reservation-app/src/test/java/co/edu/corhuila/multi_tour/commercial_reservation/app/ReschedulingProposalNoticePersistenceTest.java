package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.StoredNotice;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-015 AC-15.5, RN-06 over the real schema (-db 046): the notice of a rescheduling proposal is a ClientNotified row with the closed payload the database accepts, triggered by ReschedulingProposed
// (DD-6 of spec 018, PROPOSED default); a second write of the same proposal leaves one row, and the reader gives the type. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReschedulingProposalNoticePersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private UUID reservationId;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        reservationId = UUID.randomUUID();
        new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc)).save(fixture.reservation(reservationId, true));
    }

    private List<Map<String, Object>> notices() {
        return fixture.jdbc.queryForList("SELECT outbox_event_id, payload::text AS payload FROM commercial_reservation.outbox_event WHERE tenant_id = ? AND reservation_id = ? "
                + "AND event_type = 'ClientNotified' AND payload ->> 'notificationType' = 'RESCHEDULING_PROPOSED'", fixture.tenant, reservationId);
    }

    @Test
    void aProposalWritesOneNoticeWithTheClosedPayloadAndARepeatLeavesOne() {
        UUID proposal = UUID.randomUUID();
        var outbox = new JdbcClientNoticeOutbox(fixture.jdbc);

        outbox.append(fixture.tenant, reservationId, NoticeType.RESCHEDULING_PROPOSED, "ReschedulingProposed", proposal, NOW);
        outbox.append(fixture.tenant, reservationId, NoticeType.RESCHEDULING_PROPOSED, "ReschedulingProposed", proposal, NOW);

        assertThat(notices()).hasSize(1);
        String payload = (String) notices().get(0).get("payload");
        assertThat(payload).contains("ReschedulingProposed", proposal.toString(), fixture.customer.toString()).doesNotContain("amount", "reason", "@");
    }

    @Test
    void theReaderGivesTheTypeOfTheProposalNotice() {
        new JdbcClientNoticeOutbox(fixture.jdbc).append(fixture.tenant, reservationId, NoticeType.RESCHEDULING_PROPOSED, "ReschedulingProposed", UUID.randomUUID(), NOW);

        StoredNotice stored = new JdbcClientNoticeReader(fixture.jdbc).find(fixture.tenant, (UUID) notices().get(0).get("outbox_event_id")).orElseThrow();

        assertThat(stored.type()).isEqualTo(NoticeType.RESCHEDULING_PROPOSED);
    }
}
