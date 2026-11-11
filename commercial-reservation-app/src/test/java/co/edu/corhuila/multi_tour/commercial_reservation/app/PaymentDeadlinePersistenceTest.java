package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcInitialDeadlineRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcInitialWaitPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentDeadlineWriter;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentExtensionPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPendingPaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameterValues;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-011 over the real schema (api tasks T-02, T-04 to T-06): the deadline with its extension and freeze, the maximum of the payment
// condition, the append-only follow-up, the pending list, and the cancellation that writes status, cause and instant in one UPDATE
// (chk_reservation_cancellation_coherent) and the event ReservationCancelled. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PaymentDeadlinePersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository reservations;
    private JdbcPaymentDeadlineReader deadlines;
    private JdbcPaymentDeadlineWriter writer;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        deadlines = new JdbcPaymentDeadlineReader(fixture.jdbc);
        writer = new JdbcPaymentDeadlineWriter(fixture.jdbc);
    }

    private Reservation stored() {
        UUID id = UUID.randomUUID();
        reservations.save(fixture.reservation(id, true));
        return reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
    }

    private void deadline(Reservation reservation, String kind, Instant due) {
        fixture.jdbc.update("INSERT INTO commercial_reservation.payment_deadline (payment_deadline_id, tenant_id, reservation_id, deadline_kind, due_at) "
                + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), fixture.tenant, reservation.reservationId(), kind,
                OffsetDateTime.ofInstant(due, ZoneOffset.UTC));
    }

    @Test
    void theSingleExtensionIsStoredOnceAndTheDeadlineReadsItBack() {
        Reservation reservation = stored();
        deadline(reservation, "INITIAL_PAYMENT", NOW.plusSeconds(86_400));

        writer.saveExtension(fixture.tenant, reservation.reservationId(), 24, NOW, fixture.actor);

        var found = deadlines.openDeadline(fixture.tenant, reservation.reservationId()).orElseThrow();
        assertThat(found.extensionHours()).isEqualTo(24);
        assertThat(found.extensionGrantedAt()).isEqualTo(NOW);
        assertThat(found.extensionGrantedBy()).isEqualTo(fixture.actor);
        assertThat(found.frozen()).isFalse();
        assertThatThrownBy(() -> writer.saveExtension(fixture.tenant, reservation.reservationId(), 1, NOW, fixture.actor)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> writer.saveExtension(UUID.randomUUID(), reservation.reservationId(), 1, NOW, fixture.actor))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFrozenDeadlineReadsItsFreeze() {
        Reservation reservation = stored();
        deadline(reservation, "INITIAL_PAYMENT", NOW.plusSeconds(86_400));
        fixture.jdbc.update("UPDATE commercial_reservation.payment_deadline SET frozen_at = ?, remaining_at_freeze_minutes = 90 WHERE reservation_id = ?",
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), reservation.reservationId());

        var found = deadlines.openDeadline(fixture.tenant, reservation.reservationId()).orElseThrow();

        assertThat(found.frozen()).isTrue();
        assertThat(found.remainingAtFreezeMinutes()).isEqualTo(90);
    }

    @Test
    void theMaximumExtensionIsTheServiceRowElseTheTenantRowElseNothing() {
        Reservation reservation = stored();
        var policy = new JdbcPaymentExtensionPolicy(fixture.jdbc);
        var parameters = new JdbcPaymentParameterRepository(fixture.jdbc);
        assertThat(policy.maximumHours(fixture.tenant, reservation)).isEmpty();

        parameters.save(fixture.tenant, new PaymentParameter(new PaymentParameterValues(PaymentType.DEPOSIT, null, 24, 48, 3_000, 72, true, true),
                fixture.actor, "Tenant level", NOW));
        assertThat(policy.maximumHours(fixture.tenant, reservation)).contains(48);

        parameters.save(fixture.tenant, new PaymentParameter(new PaymentParameterValues(PaymentType.DEPOSIT, fixture.service, 24, 12, 3_000, 72, true, true),
                fixture.actor, "This tour", NOW));
        assertThat(policy.maximumHours(fixture.tenant, reservation)).contains(12);
        assertThat(policy.maximumHours(UUID.randomUUID(), reservation)).isEmpty();
    }

    @Test
    void followupsAreAppendedNewestFirstAndNeverCrossTenants() {
        Reservation reservation = stored();
        var followups = new JdbcPaymentFollowupRepository(fixture.jdbc);
        followups.append(new PaymentFollowup(UUID.randomUUID(), fixture.tenant, reservation.reservationId(), fixture.actor, NOW, "First call"));
        followups.append(new PaymentFollowup(UUID.randomUUID(), fixture.tenant, reservation.reservationId(), fixture.actor, NOW.plusSeconds(60), "Second call"));

        var page = followups.page(fixture.tenant, reservation.reservationId(), 1, 1);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(PaymentFollowup::note).containsExactly("Second call");
        assertThat(followups.page(fixture.tenant, reservation.reservationId(), 2, 1).items()).extracting(PaymentFollowup::note).containsExactly("First call");
        assertThat(followups.page(UUID.randomUUID(), reservation.reservationId(), 1, 20).total()).isZero();
    }

    @Test
    void thePendingListCarriesTheEffectiveDeadlineAndLeavesFrozenAndOtherTenantsOut() {
        Reservation open = stored();
        Reservation frozen = stored();
        deadline(open, "INITIAL_PAYMENT", NOW.plusSeconds(86_400));
        deadline(frozen, "INITIAL_PAYMENT", NOW.plusSeconds(86_400));
        fixture.jdbc.update("UPDATE commercial_reservation.payment_deadline SET frozen_at = ?, remaining_at_freeze_minutes = 5 WHERE reservation_id = ?",
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), frozen.reservationId());
        var queries = new JdbcPendingPaymentQueries(fixture.jdbc);

        var page = queries.page(fixture.tenant, null, 1, 20);

        assertThat(page.total()).isEqualTo(1);
        var row = page.items().get(0);
        assertThat(row.reservationId()).isEqualTo(open.reservationId());
        assertThat(row.customerName()).isEqualTo("Lead Ficticio");
        assertThat(row.deadlineKind()).isEqualTo(DeadlineKind.INITIAL_PAYMENT);
        assertThat(row.effectiveDeadline()).isEqualTo(NOW.plusSeconds(86_400));
        assertThat(row.hasProofInValidation()).isFalse();
        assertThat(queries.page(fixture.tenant, DeadlineKind.BALANCE, 1, 20).total()).isZero();
        assertThat(queries.page(UUID.randomUUID(), null, 1, 20).total()).isZero();
    }

    @Test
    void theCancellationWritesStatusCauseAndInstantInOneUpdateAndItsEvent() {
        Reservation reservation = stored();
        var lockedAgain = reservations.lockByTenantAndId(fixture.tenant, reservation.reservationId());
        assertThat(lockedAgain).isPresent();
        assertThat(reservations.lockByTenantAndId(UUID.randomUUID(), reservation.reservationId())).isEmpty();
        reservation.cancel(CancellationCause.PAYMENT_EXPIRY, null, NOW, 0);

        reservations.save(reservation);
        var outbox = new JdbcPaymentEventOutbox(fixture.jdbc);
        reservation.pullEvents().forEach(outbox::append);

        var again = reservations.findByTenantAndId(fixture.tenant, reservation.reservationId()).orElseThrow();
        assertThat(again.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(fixture.jdbc.queryForObject("SELECT cancellation_cause || ':' || (cancelled_at IS NOT NULL) || ':' || (cancelled_by IS NULL) FROM "
                + "commercial_reservation.reservation WHERE reservation_id = ?", String.class, reservation.reservationId()))
                .isEqualTo("PAYMENT_EXPIRY:true:true");
        assertThat(fixture.count("outbox_event", reservation.reservationId())).isEqualTo(2);
        assertThat(fixture.jdbc.queryForObject("SELECT payload::text FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'ReservationCancelled'", String.class, reservation.reservationId())).doesNotContain("note").contains("PAYMENT_EXPIRY");
        reservations.save(again);
        assertThat(reservations.findByTenantAndId(fixture.tenant, reservation.reservationId()).orElseThrow().reservationStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void theLateCancellationOfADecisionAfterTheDeadlineIsReallyApplied() {
        Reservation reservation = stored();
        var cancellation = new JdbcReservationCancellation(fixture.jdbc);

        cancellation.cancelForNonPayment(fixture.tenant, reservation.reservationId(), NOW);
        cancellation.cancelForNonPayment(fixture.tenant, reservation.reservationId(), NOW.plusSeconds(5));

        assertThat(fixture.jdbc.queryForObject("SELECT reservation_status || ':' || cancellation_cause FROM commercial_reservation.reservation "
                + "WHERE reservation_id = ?", String.class, reservation.reservationId())).isEqualTo("CANCELLED:PAYMENT_EXPIRY");
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'ReservationCancelled'", Integer.class, reservation.reservationId())).isEqualTo(1);
    }

    @Test
    void aFollowupIsFoundByItsIdInsideItsTenantOnly() {
        Reservation reservation = stored();
        var followups = new JdbcPaymentFollowupRepository(fixture.jdbc);
        UUID id = UUID.randomUUID();
        followups.append(new PaymentFollowup(id, fixture.tenant, reservation.reservationId(), fixture.actor, NOW, "Replay me"));

        assertThat(followups.find(fixture.tenant, id)).map(PaymentFollowup::note).contains("Replay me");
        assertThat(followups.find(UUID.randomUUID(), id)).isEmpty();
        assertThat(followups.find(fixture.tenant, UUID.randomUUID())).isEmpty();
    }

    @Test
    void theInitialWaitIsTheServiceRowElseTheTenantRowAndTheInitialDeadlineIsWrittenOnce() {
        Reservation reservation = stored();
        var waits = new JdbcInitialWaitPolicy(fixture.jdbc);
        var parameters = new JdbcPaymentParameterRepository(fixture.jdbc);
        assertThat(waits.initialWaitHours(fixture.tenant, reservation)).isEmpty();
        parameters.save(fixture.tenant, new PaymentParameter(new PaymentParameterValues(PaymentType.DEPOSIT, null, 24, 48, 3_000, 72, true, true),
                fixture.actor, "Tenant level", NOW));
        assertThat(waits.initialWaitHours(fixture.tenant, reservation)).contains(24);
        parameters.save(fixture.tenant, new PaymentParameter(new PaymentParameterValues(PaymentType.DEPOSIT, fixture.service, 6, 12, 3_000, 72, true, true),
                fixture.actor, "This tour", NOW));
        assertThat(waits.initialWaitHours(fixture.tenant, reservation)).contains(6);

        var recorder = new JdbcInitialDeadlineRecorder(fixture.jdbc);
        recorder.record(fixture.tenant, reservation.reservationId(), NOW.plusSeconds(6 * 3_600));

        var found = deadlines.openDeadline(fixture.tenant, reservation.reservationId()).orElseThrow();
        assertThat(found.kind()).isEqualTo(DeadlineKind.INITIAL_PAYMENT);
        assertThat(found.dueAt()).isEqualTo(NOW.plusSeconds(6 * 3_600));
        assertThatThrownBy(() -> recorder.record(fixture.tenant, reservation.reservationId(), NOW)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
