package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApplyCreditCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceApplications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication.AppliedBy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentValidated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationConfirmed;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03 of HU-COM-017 (-api): RN-04 to RN-07; AC-17.4 to AC-17.7. Applying a credit is a validated payment with no proof, no attempt and no cash movement: the
// pending balance goes down, the reservation confirms when the last needed value arrives, the first payment holds the seat or applies nothing, and the credit of a
// customer never reaches a reservation of another customer or tenant. The persistence adapters and the routes are later pull requests; the ports have doubles here.
class ApplyCreditBalanceUseCaseTest {

    private static final UUID TENANT = new UUID(17, 1), ADMIN = new UUID(17, 2), CUSTOMER = new UUID(17, 3), OTHER_CUSTOMER = new UUID(17, 4),
            SERVICE = new UUID(17, 5), DEPARTURE = new UUID(17, 6), SOURCE = new UUID(17, 7), TARGET = new UUID(17, 8), CREDIT = new UUID(17, 9);
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final Map<UUID, CreditBalance> credits = new LinkedHashMap<>();
    private final List<CreditApplication> applications = new ArrayList<>();
    private final CreditBalanceRepository creditRepository = new CreditBalanceRepository() {
        @Override public Optional<CreditBalance> find(UUID tenantId, UUID id) { return Optional.ofNullable(credits.get(id)).filter(c -> c.tenantId().equals(tenantId)); }
        @Override public Optional<CreditBalance> findByDecision(UUID tenantId, UUID decisionId) { return Optional.empty(); }
        @Override public Optional<CreditBalance> findByAttempt(UUID tenantId, UUID attemptId) { return Optional.empty(); }
        @Override public void save(CreditBalance credit) { credits.put(credit.id(), credit); }
    };
    private final CreditBalanceApplications applicationStore = applications::add;
    private final ApplyCreditBalanceUseCase apply = new ApplyCreditBalanceUseCase(creditRepository, applicationStore, fakes, departures, fakes, fakes, fakes, fakes,
            fakes, () -> new UUID(17, 100), Clock.fixed(NOW, ZoneOffset.UTC));

    ApplyCreditBalanceUseCaseTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 0));
        reservation(SOURCE, CUSTOMER, PaymentType.FULL_PAYMENT, 300_000);
        reservation(TARGET, CUSTOMER, PaymentType.FULL_PAYMENT, 300_000);
        credits.put(CREDIT, new CreditBalance(CREDIT, TENANT, SOURCE, CreditOrigin.CUSTOMER_CANCELLATION, new Cents(200_000), new UUID(17, 50), null, NOW.minusSeconds(3_600),
                ADMIN, new Cents(0), new Cents(0)));
    }

    private void reservation(UUID id, UUID customer, PaymentType type, long finalValue) {
        fakes.reservations.put(id, Reservation.create(id, TENANT, customer, DEPARTURE, ADMIN, 2, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(finalValue), new Cents(finalValue), List.of(), type, PaymentMethod.TRANSFER, NOW.minusSeconds(7_200)));
    }

    private ApplyCreditCommand command(UUID target, long amount, Role role) {
        return new ApplyCreditCommand(TENANT, ADMIN, role, role == Role.FINAL_CUSTOMER ? CUSTOMER : null, CREDIT, target, amount);
    }

    @Test
    void applyingCountsAsAValidatedPaymentAndReducesThePendingBalance() {
        CreditApplication applied = apply.apply(command(TARGET, 100_000, Role.TENANT_ADMINISTRATOR));

        Reservation target = fakes.reservations.get(TARGET);
        assertThat(target.pendingBalance()).isEqualTo(new Cents(200_000));
        assertThat(target.attempts()).isEmpty();
        assertThat(applied.amount()).isEqualTo(new Cents(100_000));
        assertThat(applied.targetReservationId()).isEqualTo(TARGET);
        assertThat(applied.appliedBy()).isEqualTo(ADMIN);
        assertThat(applied.kind()).isEqualTo(AppliedBy.STAFF);
        assertThat(applications).containsExactly(applied);
        assertThat(fakes.events).hasAtLeastOneElementOfType(PaymentValidated.class);
    }

    @Test
    void applyingPartOfTheCreditLeavesTheRestAvailable() {
        apply.apply(command(TARGET, 80_000, Role.OPERATIONAL_COLLABORATOR));

        assertThat(credits.get(CREDIT).applied()).isEqualTo(new Cents(80_000));
        assertThat(credits.get(CREDIT).available()).isEqualTo(new Cents(120_000));
    }

    @Test
    void theCustomerRecordsTheirOwnApplicationAndStaffRecordsWhoAppliedOnTheirBehalf() {
        CreditApplication byCustomer = apply.apply(command(TARGET, 10_000, Role.FINAL_CUSTOMER));

        assertThat(byCustomer.kind()).isEqualTo(AppliedBy.CUSTOMER);
        assertThatThrownBy(() -> apply.apply(command(TARGET, 10_000, Role.ANALYST))).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> apply.apply(new ApplyCreditCommand(TENANT, ADMIN, Role.FINAL_CUSTOMER, OTHER_CUSTOMER, CREDIT, TARGET, 10_000)))
                .isInstanceOf(PaymentAccessDeniedException.class);
        assertThat(applications).hasSize(1);
    }

    @Test
    void aReservationOfAnotherCustomerIsRefused() {
        UUID foreign = new UUID(17, 20);
        reservation(foreign, OTHER_CUSTOMER, PaymentType.FULL_PAYMENT, 300_000);

        assertThatThrownBy(() -> apply.apply(command(foreign, 10_000, Role.TENANT_ADMINISTRATOR))).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> apply.apply(new ApplyCreditCommand(new UUID(17, 99), ADMIN, Role.TENANT_ADMINISTRATOR, null, CREDIT, TARGET, 10_000)))
                .isInstanceOf(CreditBalanceNotFoundException.class);
        assertThat(applications).isEmpty();
        assertThat(credits.get(CREDIT).applied()).isEqualTo(new Cents(0));
    }

    @Test
    void aCancelledFinishedOrInExecutionReservationIsRefused() {
        for (ReservationStatus status : List.of(ReservationStatus.CANCELLED, ReservationStatus.COMPLETED, ReservationStatus.IN_EXECUTION)) {
            Reservation target = fakes.reservations.get(TARGET);
            fakes.reservations.put(TARGET, target.restoredAs(status, PaymentStatus.NO_PAYMENT, new Cents(300_000), false, List.of()));
            assertThatThrownBy(() -> apply.apply(command(TARGET, 10_000, Role.TENANT_ADMINISTRATOR))).isInstanceOf(BusinessRuleViolation.class);
        }
        assertThat(applications).isEmpty();
    }

    @Test
    void anAmountAboveTheAvailableOrThePendingBalanceIsRefused() {
        assertThatThrownBy(() -> apply.apply(command(TARGET, 250_000, Role.TENANT_ADMINISTRATOR))).isInstanceOf(BusinessRuleViolation.class);
        credits.put(CREDIT, credits.get(CREDIT).withApplied(new Cents(150_000)));
        assertThatThrownBy(() -> apply.apply(command(TARGET, 60_000, Role.TENANT_ADMINISTRATOR))).isInstanceOf(BusinessRuleViolation.class);
        fakes.reservations.put(TARGET, fakes.reservations.get(TARGET).restoredAs(ReservationStatus.PENDING_PAYMENT, PaymentStatus.PARTIAL, new Cents(20_000), true, List.of()));
        assertThatThrownBy(() -> apply.apply(command(TARGET, 30_000, Role.TENANT_ADMINISTRATOR))).isInstanceOf(BusinessRuleViolation.class);
        assertThat(applications).isEmpty();
    }

    @Test
    void aStartedDepartureRefusesTheApplication() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.minusSeconds(60), DepartureStatus.SCHEDULED, 10, 0));

        assertThatThrownBy(() -> apply.apply(command(TARGET, 10_000, Role.TENANT_ADMINISTRATOR))).isInstanceOf(BusinessRuleViolation.class);
        assertThat(applications).isEmpty();
    }

    @Test
    void applyingAsTheFirstPaymentHoldsTheSeatOrAppliesNothing() {
        apply.apply(command(TARGET, 10_000, Role.TENANT_ADMINISTRATOR));
        assertThat(fakes.holdCalls).isEqualTo(1);
        assertThat(fakes.reservations.get(TARGET).seatHeld()).isTrue();

        UUID second = new UUID(17, 21);
        reservation(second, CUSTOMER, PaymentType.FULL_PAYMENT, 300_000);
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 2, 2));
        assertThatThrownBy(() -> apply.apply(command(second, 10_000, Role.TENANT_ADMINISTRATOR))).isInstanceOf(BusinessRuleViolation.class);
        assertThat(applications).hasSize(1);
        assertThat(credits.get(CREDIT).applied()).isEqualTo(new Cents(10_000));
        assertThat(fakes.reservations.get(second).pendingBalance()).isEqualTo(new Cents(300_000));
    }

    @Test
    void applyingTheLastNeededValueConfirmsTheReservation() {
        reservation(new UUID(17, 22), CUSTOMER, PaymentType.FULL_PAYMENT, 150_000);
        apply.apply(command(new UUID(17, 22), 150_000, Role.TENANT_ADMINISTRATOR));

        Reservation confirmed = fakes.reservations.get(new UUID(17, 22));
        assertThat(confirmed.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.pendingBalance()).isEqualTo(new Cents(0));
        assertThat(fakes.events).hasAtLeastOneElementOfType(ReservationConfirmed.class);
    }
}
