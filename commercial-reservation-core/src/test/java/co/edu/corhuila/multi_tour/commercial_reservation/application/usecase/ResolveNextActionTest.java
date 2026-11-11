package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NextCustomerAction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03 of HU-COM-014 (AC-14.8, RN-06): the service feeds the table of NextCustomerAction with the deadline, the departure and the
// proposal. A reservation with no deadline row is not refused for expiry (G-DL); an open proposal, until HU-COM-018, is false.
class ResolveNextActionTest {

    private static final UUID TENANT = new UUID(14, 1), CUSTOMER = new UUID(14, 2), SERVICE = new UUID(14, 3), DEPARTURE = new UUID(14, 4),
            RESERVATION = new UUID(14, 5), ADMIN = new UUID(14, 6), ATTEMPT = new UUID(14, 7), SECOND = new UUID(14, 8);
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z"), START = NOW.plus(Duration.ofDays(10));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private boolean proposalOpen;
    private final ResolveNextActionUseCase resolver = new ResolveNextActionUseCase(fakes, departures,
            (tenant, reservation) -> proposalOpen, Clock.fixed(NOW, ZoneOffset.UTC));

    private ReservationListItem row(ReservationStatus status, long pending, Optional<ValidationStatus> proof) {
        return new ReservationListItem(RESERVATION, "Persona Ficticia", "Tour Ficticio", START, status, PaymentStatus.NO_PAYMENT,
                new Cents(pending), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, new Cents(0), proof);
    }

    private Reservation reservation() {
        return Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofDays(1)));
    }

    @Test
    void aReservationWithNoDeadlineRowIsNotRefusedForExpiry() {
        assertThat(resolver.forRow(TENANT, row(ReservationStatus.PENDING_PAYMENT, 300_000, Optional.of(ValidationStatus.REJECTED))))
                .isEqualTo(NextCustomerAction.PRESENT_NEW_RECEIPT);
    }

    @Test
    void aRejectedProofOffersANewReceiptOnlyWhileTheDeadlineHolds() {
        var rejected = row(ReservationStatus.PENDING_PAYMENT, 300_000, Optional.of(ValidationStatus.REJECTED));
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.plus(Duration.ofHours(2))));
        assertThat(resolver.forRow(TENANT, rejected)).isEqualTo(NextCustomerAction.PRESENT_NEW_RECEIPT);
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.minus(Duration.ofHours(2))));
        assertThat(resolver.forRow(TENANT, rejected)).isEqualTo(NextCustomerAction.CONTINUE_PAYMENT);
    }

    @Test
    void aFrozenDeadlineHoldsEvenWhenItsDueInstantPassed() {
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.minus(Duration.ofHours(2)), null, null, null,
                NOW.minus(Duration.ofHours(3)), 60));
        assertThat(resolver.forRow(TENANT, row(ReservationStatus.PENDING_PAYMENT, 300_000, Optional.of(ValidationStatus.REJECTED))))
                .isEqualTo(NextCustomerAction.PRESENT_NEW_RECEIPT);
    }

    @Test
    void anOpenProposalOffersNoAction() {
        proposalOpen = true;
        assertThat(resolver.forRow(TENANT, row(ReservationStatus.PENDING_PAYMENT, 300_000, Optional.empty())))
                .isEqualTo(NextCustomerAction.NONE);
    }

    @Test
    void theReadOfOneReservationUsesTheLatestAttemptAndTheDepartureStart() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, START, DepartureStatus.SCHEDULED, 10, 2));
        Reservation reservation = reservation();
        assertThat(resolver.forReservation(reservation)).isEqualTo(NextCustomerAction.CONTINUE_PAYMENT);
        reservation.registerTransfer(ATTEMPT, new Cents(50_000), null, ADMIN, NOW.minus(Duration.ofHours(3)));
        reservation.reject(ATTEMPT, "illegible", ADMIN, NOW.minus(Duration.ofHours(2)));
        assertThat(resolver.forReservation(reservation)).isEqualTo(NextCustomerAction.PRESENT_NEW_RECEIPT);
        reservation.registerTransfer(SECOND, new Cents(50_000), null, ADMIN, NOW.minus(Duration.ofHours(1)));
        assertThat(resolver.forReservation(reservation)).isEqualTo(NextCustomerAction.NONE);
    }
}
