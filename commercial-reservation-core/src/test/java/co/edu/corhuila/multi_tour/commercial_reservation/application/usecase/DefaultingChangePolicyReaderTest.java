package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationQuote.Reason;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingDifferenceDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangePolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ModificationOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-012 DP-01 (PROPOSED DEFAULT, 2026-11-12): a tour with no stored terms uses the default of the PDR for MODIFICATION, an explicit row
// always prevails, and DATE_CHANGE with no row stays refused. The stored terms are a double that answers empty (a tenant with no row).
class DefaultingChangePolicyReaderTest {

    private static final UUID TENANT = new UUID(12, 0xe1), COLLABORATOR = new UUID(12, 0xe2), CUSTOMER = new UUID(12, 0xe3),
            SERVICE = new UUID(12, 0xe4), DEPARTURE = new UUID(12, 0xe5), OTHER = new UUID(12, 0xe6), RESERVATION = new UUID(12, 0xe7);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant START = CREATED.plus(Duration.ofDays(30));
    private static final ReservedService TOUR = new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final List<Instant> differences = new ArrayList<>();
    private Optional<ChangePolicy> stored = Optional.empty();
    private final ChangePolicyReader terms = new DefaultingChangePolicyReader((tenant, service, type) -> stored);
    private final PendingDifferenceDeadlines deadlines = new PendingDifferenceDeadlines() {
        @Override
        public OptionalInt balanceAdvanceHours(UUID tenantId, UUID serviceId, PaymentType type) {
            return OptionalInt.of(48);
        }

        @Override
        public void record(UUID tenantId, UUID reservationId, Instant dueAt) {
            differences.add(dueAt);
        }
    };
    private final ExcessDecisionOpener decisions = (tenant, reservation, excess, at) -> { };

    DefaultingChangePolicyReaderTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, START, DepartureStatus.SCHEDULED, 10, 2));
        departures.plant(new DepartureState(OTHER, TENANT, SERVICE, START.plus(Duration.ofDays(7)), DepartureStatus.SCHEDULED, 10, 0));
    }

    private Clock clockAt(Duration beforeStart) {
        return Clock.fixed(START.minus(beforeStart), ZoneOffset.UTC);
    }

    private PaymentActor actor() {
        return new PaymentActor(TENANT, COLLABORATOR, Role.OPERATIONAL_COLLABORATOR);
    }

    private Reservation planted(PaymentType type, long paid) {
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, COLLABORATOR, 2, TOUR, new Cents(300_000),
                new Cents(300_000), List.of(), type, PaymentMethod.CASH, CREATED);
        if (paid > 0) {
            reservation.registerCash(new UUID(12, 0xe8), new Cents(paid), new Cents(100_000), COLLABORATOR, CREATED);
            reservation.allocateSeat(CREATED);
        }
        reservation.pullEvents();
        fakes.reservations.put(RESERVATION, reservation);
        return reservation;
    }

    private ModificationQuote quote(Duration beforeStart, ChangeType type) {
        return new QuoteModificationUseCase(fakes, departures, terms, clockAt(beforeStart)).quote(actor(), RESERVATION,
                new ModificationRequest(type, 3, type == ChangeType.DATE_CHANGE ? OTHER : DEPARTURE, List.of()));
    }

    private ModificationOutcome modify(Duration beforeStart, ChangeType type) {
        return new ModifyReservationUseCase(fakes, departures, terms, fakes, fakes, decisions, deadlines, new ReservationModificationFakes(), fakes,
                fakes, clockAt(beforeStart)).modify(actor(), RESERVATION,
                new ModificationRequest(type, 3, type == ChangeType.DATE_CHANGE ? OTHER : DEPARTURE, List.of()), "The customer asked for it").outcome();
    }

    @Test
    void aTenantWithoutStoredTermsMayModifyWithTheDefaultOfThePdr() {
        planted(PaymentType.FULL_PAYMENT, 0);

        ModificationQuote answer = quote(Duration.ofDays(28), ChangeType.MODIFICATION);

        assertThat(answer.allowed()).isTrue();
        assertThat(answer.reason()).isNull();
        assertThat(terms.policyFor(TENANT, SERVICE, ChangeType.MODIFICATION)).contains(new ChangePolicy(true, 360));
    }

    @Test
    void aPendingPaymentReservationIsRecomputedWithTheDefaultTerms() {
        Reservation reservation = planted(PaymentType.FULL_PAYMENT, 0);

        ModificationOutcome outcome = modify(Duration.ofDays(28), ChangeType.MODIFICATION);

        assertThat(outcome.newFinalValue()).isEqualTo(new Cents(450_000));
        assertThat(reservation.people()).isEqualTo(3);
        assertThat(fakes.saved).containsExactly(reservation);
    }

    @Test
    void anIncreaseOnAConfirmedReservationKeepsTheStatusWithTheDefaultTerms() {
        Reservation reservation = planted(PaymentType.DEPOSIT, 300_000);

        ModificationOutcome outcome = modify(Duration.ofDays(28), ChangeType.MODIFICATION);

        assertThat(outcome.newPendingBalance()).isEqualTo(new Cents(150_000));
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(differences).containsExactly(START.minus(Duration.ofHours(48)));
    }

    @Test
    void theDefaultLeadTimeIsSixHoursBeforeTheDepartureStart() {
        planted(PaymentType.FULL_PAYMENT, 0);

        assertThat(quote(Duration.ofHours(5), ChangeType.MODIFICATION).reason()).isEqualTo(Reason.TOO_LATE);
        assertThat(quote(Duration.ofHours(7), ChangeType.MODIFICATION).allowed()).isTrue();
    }

    @Test
    void anExplicitRowThatForbidsTheChangePrevailsOverTheDefault() {
        planted(PaymentType.FULL_PAYMENT, 0);
        stored = Optional.of(new ChangePolicy(false, 0));

        assertThat(quote(Duration.ofDays(28), ChangeType.MODIFICATION).reason()).isEqualTo(Reason.NOT_ALLOWED);
        assertThatThrownBy(() -> modify(Duration.ofDays(28), ChangeType.MODIFICATION)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(fakes.saved).isEmpty();
    }

    @Test
    void anExplicitRowWithAShorterLeadTimePrevailsOverTheDefault() {
        planted(PaymentType.FULL_PAYMENT, 0);
        stored = Optional.of(new ChangePolicy(true, 60));

        assertThat(quote(Duration.ofHours(5), ChangeType.MODIFICATION).allowed()).isTrue();
        assertThat(terms.policyFor(TENANT, SERVICE, ChangeType.MODIFICATION)).contains(new ChangePolicy(true, 60));
    }

    @Test
    void aDateChangeWithoutStoredTermsStaysRefusedAndNothingChanges() {
        planted(PaymentType.FULL_PAYMENT, 0);

        assertThat(terms.policyFor(TENANT, SERVICE, ChangeType.DATE_CHANGE)).isEmpty();
        assertThat(quote(Duration.ofDays(28), ChangeType.DATE_CHANGE).reason()).isEqualTo(Reason.NO_POLICY);
        assertThatThrownBy(() -> modify(Duration.ofDays(28), ChangeType.DATE_CHANGE)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(fakes.saved).isEmpty();
    }
}
