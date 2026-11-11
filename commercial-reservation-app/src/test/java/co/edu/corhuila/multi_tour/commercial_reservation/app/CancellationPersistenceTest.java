package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cancellation.DeferredFinancialDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cancellation.NoCancellationPenaltyPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cancellation.NoReprogrammingMark;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCancellationRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CancelReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-013 over the real schema (api tasks T-04 to T-06; AC-13.1, AC-13.8 to AC-13.10, AC-13.12, RNF-06): the use case with the real
// adapters cancels once, with the cause and instant the engine requires, writes the settlement record the key of the db accepts, writes
// ReservationCancelled once and never lets the record be edited. Another tenant sees nothing. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CancellationPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository reservations;
    private JdbcCancellationRecords records;
    private CancelReservationUseCase cancel;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        records = new JdbcCancellationRecords(fixture.jdbc);
        cancel = new CancelReservationUseCase(reservations, new JdbcDepartureReader(fixture.jdbc), new JdbcSalidaCapacityHolder(fixture.jdbc),
                new NoCancellationPenaltyPolicy(), new NoReprogrammingMark(), new DeferredFinancialDecisionOpener(), records,
                new JdbcPaymentEventOutbox(fixture.jdbc), new JdbcPaymentTransaction(fixture.transactions), Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
    }

    private Reservation stored() {
        UUID id = UUID.randomUUID();
        reservations.save(fixture.reservation(id, true));
        return reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
    }

    private CancelReservationCommand command(Reservation reservation) {
        return new CancelReservationCommand(new PaymentActor(fixture.tenant, fixture.actor, Role.OPERATIONAL_COLLABORATOR), reservation.reservationId(),
                "The customer cannot travel", "CUSTOMER_REQUEST");
    }

    @Test
    void theCancellationStoresTheStatusTheCauseTheRecordAndTheEventOnceAndASecondOneIsRefused() {
        Reservation reservation = stored();

        var record = cancel.cancel(command(reservation));

        assertThat(fixture.jdbc.queryForObject("SELECT reservation_status || ':' || cancellation_cause || ':' || cancelled_by FROM "
                + "commercial_reservation.reservation WHERE reservation_id = ?", String.class, reservation.reservationId()))
                .isEqualTo("CANCELLED:CUSTOMER_REQUEST:" + fixture.actor);
        var stored = records.find(fixture.tenant, reservation.reservationId()).orElseThrow();
        assertThat(stored).isEqualTo(record);
        assertThat(stored.reason()).isEqualTo("The customer cannot travel");
        assertThat(stored.settlement().penalty().amount()).isZero();
        assertThat(stored.settlement().refundable()).isEqualTo(stored.settlement().validated());
        assertThat(stored.cancelledAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'ReservationCancelled' AND payload ->> 'cause' = 'CUSTOMER_REQUEST'", Integer.class, reservation.reservationId()))
                .isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND payload::text LIKE '%cannot travel%'", Integer.class, reservation.reservationId())).isZero();
        assertThatThrownBy(() -> cancel.cancel(command(reservation))).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.reservation_cancellation WHERE reservation_id = ?",
                Integer.class, reservation.reservationId())).isEqualTo(1);
    }

    @Test
    void theRecordCannotBeEditedOrDeletedByTheApplicationUser() {
        Reservation reservation = stored();
        cancel.cancel(command(reservation));

        assertThatThrownBy(() -> fixture.jdbc.update("UPDATE commercial_reservation.reservation_cancellation SET reason = 'edited' "
                + "WHERE reservation_id = ?", reservation.reservationId())).hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> fixture.jdbc.update("DELETE FROM commercial_reservation.reservation_cancellation WHERE reservation_id = ?",
                reservation.reservationId())).hasStackTraceContaining("permission denied");
    }

    @Test
    void anotherTenantSeesNoRecordAndCannotCancelTheReservation() {
        Reservation reservation = stored();
        cancel.cancel(command(reservation));
        UUID stranger = UUID.randomUUID();

        assertThat(records.find(stranger, reservation.reservationId())).isEmpty();
        assertThatThrownBy(() -> cancel.cancel(new CancelReservationCommand(new PaymentActor(stranger, fixture.actor, Role.TENANT_ADMINISTRATOR),
                reservation.reservationId(), "Because", "CUSTOMER_REQUEST"))).isInstanceOf(ReservationNotFoundException.class);
    }
}
