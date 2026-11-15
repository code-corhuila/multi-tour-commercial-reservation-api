package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.calendar.WeekdayBusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundBasisReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EmergencyOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DetermineRefundUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PersonalEmergencyUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RefundAutomationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PersonalEmergency;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// Spec 024 (-api), T-03 (AC-24.1, AC-24.11) over the real schema: the refund, its RefundAuthorized event and the processed mark commit together. A failure after the
// refund and before the mark leaves neither. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PersonalEmergencyPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private JdbcRefundAuthorizationRepository refunds;
    private JdbcProcessedEvents processed;
    private JdbcReservationRepository reservations;
    private JdbcPaymentTransaction transaction;
    private UUID reservation;

    @BeforeEach
    void plant() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        refunds = new JdbcRefundAuthorizationRepository(fixture.jdbc);
        processed = new JdbcProcessedEvents(fixture.jdbc);
        transaction = new JdbcPaymentTransaction(fixture.transactions);
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        reservation = UUID.randomUUID();
        reservations.save(fixture.reservation(reservation, true));
    }

    private PersonalEmergencyUseCase useCase(ProcessedEvents marks) {
        var automation = new RefundAutomationUseCase(new JdbcFinancialDecisionRepository(fixture.jdbc), refunds, new WeekdayBusinessCalendar(),
                new JdbcRefundAuthorizedOutbox(fixture.jdbc), UUID::randomUUID, transaction, Clock.fixed(NOW, ZoneOffset.UTC));
        var determine = new DetermineRefundUseCase(new JdbcRefundBasisReader(fixture.jdbc), (tenant, service, start, at) -> OptionalInt.of(0), refunds, automation,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new PersonalEmergencyUseCase(marks, reservations, refunds, determine, transaction);
    }

    private PersonalEmergency event(UUID eventId, int affected) {
        return new PersonalEmergency(eventId, fixture.tenant, fixture.salida, reservation, affected, NOW);
    }

    @Test
    void appliesTheEventOnceAndMarksItInTheSameTransaction() {
        UUID eventId = UUID.randomUUID();
        var useCase = useCase(processed);

        // The reservation of the fixture is not cancelled and has nothing validated, so the refund is "nothing due" and the event is marked.
        assertThat(useCase.apply(event(eventId, 1))).isIn(EmergencyOutcome.NO_REFUND, EmergencyOutcome.REFUND_DETERMINED);
        assertThat(processed.wasProcessed(eventId)).isTrue();
        assertThat(useCase.apply(event(eventId, 1))).isEqualTo(EmergencyOutcome.DUPLICATE);
        assertThat(refunds.findByReservationAndOrigin(fixture.tenant, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION).stream().count()).isLessThanOrEqualTo(1);
    }

    @Test
    void aFailureAfterTheRefundAndBeforeTheMarkLeavesNeither() {
        reservations.save(paid(reservation));
        UUID eventId = UUID.randomUUID();
        ProcessedEvents failing = new ProcessedEvents() {
            @Override public boolean wasProcessed(UUID id) { return processed.wasProcessed(id); }
            @Override public void record(UUID id) { throw new IllegalStateException("the mark could not be written"); }
        };

        assertThatThrownBy(() -> useCase(failing).apply(event(eventId, 1))).isInstanceOf(IllegalStateException.class);

        assertThat(processed.wasProcessed(eventId)).isFalse();
        assertThat(refunds.findByReservationAndOrigin(fixture.tenant, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION)).isEmpty();
    }

    // The fixture reservation has its whole value pending; this one is read back as paid so that a refund is above zero.
    private co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation paid(UUID id) {
        var stored = reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
        return stored.restoredAs(co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus.IN_EXECUTION,
                co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus.FULLY_PAID,
                new co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents(0), false, java.util.List.of());
    }
}
