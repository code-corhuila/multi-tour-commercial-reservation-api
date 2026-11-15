package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExtendDeadlineCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-02 of HU-COM-011: RN-03; AC-11.3. One extension, before the expiry, of an open initial deadline of a pending reservation, from one
// hour to the maximum of the payment condition and never past the departure start. The refusals leave the deadline untouched.
class ExtendPaymentDeadlineTest {

    private static final UUID TENANT = new UUID(11, 0xb1), ADMIN = new UUID(11, 0xb2), CUSTOMER = new UUID(11, 0xb3), SERVICE = new UUID(11, 0xb4),
            DEPARTURE = new UUID(11, 0xb5), RESERVATION = new UUID(11, 0xb6), OTHER_TENANT = new UUID(11, 0xb7);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant NOW = CREATED.plus(Duration.ofHours(2));
    private static final Instant DUE = CREATED.plus(Duration.ofHours(24));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final ExpiryFakes expiry = new ExpiryFakes(fakes);
    private final ExtendPaymentDeadlineUseCase extend = new ExtendPaymentDeadlineUseCase(fakes, fakes, departures, expiry, expiry, fakes,
            Clock.fixed(NOW, ZoneOffset.UTC));

    ExtendPaymentDeadlineTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, DUE.plus(Duration.ofDays(30)), DepartureStatus.SCHEDULED, 10, 0));
        planted(DeadlineKind.INITIAL_PAYMENT);
    }

    private void planted(DeadlineKind kind) {
        fakes.reservations.put(RESERVATION, Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, CREATED));
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(kind, DUE));
    }

    private static PaymentActor actor(Role role) {
        return new PaymentActor(TENANT, ADMIN, role);
    }

    private DeadlineView grant(Integer hours) {
        return extend.extend(new ExtendDeadlineCommand(actor(Role.OPERATIONAL_COLLABORATOR), RESERVATION, hours));
    }

    @Test
    void aSingleExtensionIsRecordedWithDurationDateTimeAndPerson() {
        DeadlineView view = grant(24);

        assertThat(expiry.extensions).containsExactly("24h by " + ADMIN + " at " + NOW);
        assertThat(view.deadline().extensionHours()).isEqualTo(24);
        assertThat(view.effective()).isEqualTo(DUE.plus(Duration.ofHours(24)));
        assertThat(fakes.deadlines.get(RESERVATION).extensionGrantedBy()).isEqualTo(ADMIN);
    }

    @Test
    void aSecondExtensionIsRefused() {
        grant(24);

        assertThatThrownBy(() -> grant(1)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(expiry.extensions).hasSize(1);
        assertThat(fakes.deadlines.get(RESERVATION).extensionHours()).isEqualTo(24);
    }

    @Test
    void anExtensionAfterExpiryOrAboveTheMaximumIsRefused() {
        assertThatThrownBy(() -> grant(49)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> grant(0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> grant(null)).isInstanceOf(DomainException.class);
        expiry.maximumHours = null;
        assertThatThrownBy(() -> grant(1)).isInstanceOf(BusinessRuleViolation.class);
        expiry.maximumHours = 48;

        Extender late = new Extender(Clock.fixed(DUE, ZoneOffset.UTC));
        assertThatThrownBy(() -> late.grant(1)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(expiry.extensions).isEmpty();
    }

    @Test
    void anExtensionNeverPassesTheDepartureStartNorTouchesAFrozenOrNonInitialDeadline() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, DUE.plus(Duration.ofHours(10)), DepartureStatus.SCHEDULED, 10, 0));
        assertThatThrownBy(() -> grant(24)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(grant(10).effective()).isEqualTo(DUE.plus(Duration.ofHours(10)));

        planted(DeadlineKind.BALANCE);
        assertThatThrownBy(() -> grant(1)).isInstanceOf(BusinessRuleViolation.class);
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE, null, null, null, NOW, 600));
        assertThatThrownBy(() -> grant(1)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(expiry.extensions).hasSize(1);
    }

    @Test
    void onlyAdministratorAndCollaboratorWriteAndAnotherTenantAnswersNotFound() {
        for (Role role : List.of(Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER)) {
            assertThatThrownBy(() -> extend.extend(new ExtendDeadlineCommand(actor(role), RESERVATION, 1)))
                    .isInstanceOf(PaymentAccessDeniedException.class);
        }
        assertThatThrownBy(() -> extend.extend(new ExtendDeadlineCommand(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR),
                RESERVATION, 1))).isInstanceOf(ReservationNotFoundException.class);
        assertThat(expiry.extensions).isEmpty();
    }

    // The same use case with another clock, for the expired case.
    private final class Extender {
        private final ExtendPaymentDeadlineUseCase other;

        Extender(Clock clock) {
            other = new ExtendPaymentDeadlineUseCase(fakes, fakes, departures, expiry, expiry, fakes, clock);
        }

        DeadlineView grant(int hours) {
            return other.extend(new ExtendDeadlineCommand(actor(Role.TENANT_ADMINISTRATOR), RESERVATION, hours));
        }
    }
}
