package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationQuote.Reason;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangePolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-02 of HU-COM-012: RN-03, RN-09, RN-11; AC-12.2, AC-12.10. The terms are a double (DP-01): the record has no screen and no story fills it.
class QuoteModificationTest {

    private static final UUID TENANT = new UUID(12, 0xb1), COLLABORATOR = new UUID(12, 0xb2), CUSTOMER = new UUID(12, 0xb3),
            SERVICE = new UUID(12, 0xb4), DEPARTURE = new UUID(12, 0xb5), RESERVATION = new UUID(12, 0xb6), OTHER_TENANT = new UUID(12, 0xb7);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant NOW = CREATED.plus(Duration.ofDays(2));
    private static final Instant START = CREATED.plus(Duration.ofDays(30));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private Optional<ChangePolicy> policy = Optional.empty();
    private final ChangePolicyReader terms = (tenant, service, type) -> policy;
    private final QuoteModificationUseCase quote = new QuoteModificationUseCase(fakes, departures, terms, Clock.fixed(NOW, ZoneOffset.UTC));

    QuoteModificationTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, START, DepartureStatus.SCHEDULED, 10, 2));
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, COLLABORATOR, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, CREATED);
        reservation.restoredAs(ReservationStatus.PENDING_PAYMENT, PaymentStatus.NO_PAYMENT, new Cents(300_000), false, List.of());
        fakes.reservations.put(RESERVATION, reservation);
    }

    private static PaymentActor actor(Role role) {
        return new PaymentActor(TENANT, COLLABORATOR, role);
    }

    private ModificationQuote ask() {
        return quote.quote(actor(Role.OPERATIONAL_COLLABORATOR), RESERVATION,
                new ModificationRequest(ChangeType.MODIFICATION, 3, DEPARTURE, List.of()));
    }

    @Test
    void noPolicyOrNotAllowedOrTooLateRefusesTheChange() {
        policy = Optional.empty();
        assertThat(ask().allowed()).isFalse();
        assertThat(ask().reason()).isEqualTo(Reason.NO_POLICY);

        policy = Optional.of(new ChangePolicy(false, 0));
        assertThat(ask().reason()).isEqualTo(Reason.NOT_ALLOWED);

        policy = Optional.of(new ChangePolicy(true, 31 * 24 * 60));
        assertThat(ask().reason()).isEqualTo(Reason.TOO_LATE);

        policy = Optional.of(new ChangePolicy(true, 28 * 24 * 60));
        assertThat(ask().allowed()).isTrue();
        assertThat(ask().reason()).isNull();
    }

    @Test
    void theRefusalNamesItsReasonAndChangesNothing() {
        policy = Optional.empty();

        ModificationQuote refused = ask();

        assertThat(refused.reason()).isEqualTo(Reason.NO_POLICY);
        assertThat(refused.outcome()).isNull();
        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.reservations.get(RESERVATION).people()).isEqualTo(2);
    }

    @Test
    void theQuoteCarriesNoSideEffect() {
        policy = Optional.of(new ChangePolicy(true, 60));

        ModificationQuote answer = ask();

        assertThat(answer.allowed()).isTrue();
        assertThat(answer.outcome().newFinalValue()).isEqualTo(new Cents(450_000));
        assertThat(answer.outcome().seatDelta()).isEqualTo(1);
        Reservation stored = fakes.reservations.get(RESERVATION);
        assertThat(stored.people()).isEqualTo(2);
        assertThat(stored.finalValue()).isEqualTo(new Cents(300_000));
        assertThat(stored.pendingBalance()).isEqualTo(new Cents(300_000));
        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void onlyTheAdministratorAndTheCollaboratorQuoteAndAnotherTenantLooksMissing() {
        policy = Optional.of(new ChangePolicy(true, 60));
        for (Role role : List.of(Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST)) {
            assertThatThrownBy(() -> quote.quote(actor(role), RESERVATION, new ModificationRequest(ChangeType.MODIFICATION, 3, DEPARTURE, List.of())))
                    .isInstanceOf(PaymentAccessDeniedException.class);
        }
        assertThatThrownBy(() -> quote.quote(new PaymentActor(OTHER_TENANT, COLLABORATOR, Role.TENANT_ADMINISTRATOR), RESERVATION,
                new ModificationRequest(ChangeType.MODIFICATION, 3, DEPARTURE, List.of()))).isInstanceOf(ReservationNotFoundException.class);
    }
}
