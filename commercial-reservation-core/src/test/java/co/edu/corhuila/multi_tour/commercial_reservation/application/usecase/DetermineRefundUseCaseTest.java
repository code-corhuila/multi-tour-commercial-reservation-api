package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundBasis;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoRefundDueException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Spec 026 (-api): AC-26.1, AC-26.3, AC-26.4, AC-26.6, AC-26.7, AC-26.8. The service computes the refund; nobody gives it an amount. Doubles only.
class DetermineRefundUseCaseTest {

    private static final Instant CAUSE = Instant.parse("2026-11-09T08:00:00Z");

    private final RefundFakes fakes = new RefundFakes();
    private final UUID reservation = UUID.randomUUID();
    private final UUID service = UUID.randomUUID();
    private final List<String> policyAsked = new ArrayList<>();
    private OptionalInt basisPoints = OptionalInt.empty();
    private long validated = 180_000;
    private int people = 4;
    private final RefundAutomationUseCase automation = new RefundAutomationUseCase(fakes.decisionRepository, fakes.refundRepository, fakes.calendar,
            fakes.outbox, fakes.ids, fakes.transaction, RefundFakes.CLOCK);
    private final DetermineRefundUseCase determine = new DetermineRefundUseCase(
            (tenant, id) -> tenant.equals(RefundFakes.TENANT) && id.equals(reservation)
                    ? Optional.of(new RefundBasis(id, new Cents(validated), people, service, Instant.parse("2026-12-01T10:00:00Z"))) : Optional.empty(),
            (tenant, serviceId, start, at) -> {
                policyAsked.add(serviceId + "@" + at);
                return basisPoints;
            }, fakes.refundRepository, automation, RefundFakes.CLOCK);

    @Test
    void refundsAllThePaidAndValidatedValueForANotAttributableCause() {
        RefundAuthorization refund = determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, null);

        assertThat(refund.authorizedAmount()).isEqualTo(new Cents(180_000));
        assertThat(refund.authorizedBy()).isNull();
        assertThat(fakes.events).containsExactly(refund);
        assertThat(policyAsked).as("a cause not attributable never reads the penalty policy").isEmpty();
    }

    @Test
    void refusesANotAttributableRefundWithNothingPaid() {
        validated = 0;

        assertThatThrownBy(() -> determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, null))
                .isInstanceOf(NoRefundDueException.class);
        assertThat(fakes.refunds).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void aPersonalEmergencyRefundsTheShareOfTheAffectedTravelersMinusThePolicy() {
        basisPoints = OptionalInt.of(2_500);

        RefundAuthorization refund = determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, 1, CAUSE);

        // share 180000 * 1 / 4 = 45000; penalty 25 percent = 11250; refund 33750
        assertThat(refund.authorizedAmount()).isEqualTo(new Cents(33_750));
        assertThat(refund.origin()).isEqualTo(RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION);
    }

    @Test
    void appliesThePolicyOfTheInstantOfTheCauseNotTheInstantOfProcessing() {
        basisPoints = OptionalInt.of(0);

        determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, 2, CAUSE);

        assertThat(policyAsked).containsExactly(service + "@" + CAUSE);
    }

    @Test
    void aPersonalEmergencyWithNoPolicyRowRefundsNothing() {
        basisPoints = OptionalInt.empty();

        assertThatThrownBy(() -> determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, 1, CAUSE))
                .isInstanceOf(NoRefundDueException.class);
        assertThat(fakes.refunds).isEmpty();
    }

    @Test
    void createsNoRefundWhenTheComputedValueIsZero() {
        basisPoints = OptionalInt.of(10_000);

        assertThatThrownBy(() -> determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, 4, CAUSE))
                .isInstanceOf(NoRefundDueException.class);
        assertThat(fakes.refunds).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void returnsTheStoredRefundOnASecondDetermination() {
        RefundAuthorization first = determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, null);
        validated = 999_999;

        RefundAuthorization second = determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, null);

        assertThat(second).isEqualTo(first);
        assertThat(fakes.refunds).hasSize(1);
        assertThat(fakes.events).hasSize(1);
    }

    @Test
    void aPersonalEmergencyNeedsTheAffectedTravelersWithinThePeopleOfTheReservation() {
        basisPoints = OptionalInt.of(0);

        assertThatThrownBy(() -> determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, null, CAUSE))
                .isInstanceOf(DomainException.class).extracting("field").isEqualTo("affectedTravelers");
        assertThatThrownBy(() -> determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, 0, CAUSE))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, 5, CAUSE))
                .isInstanceOf(co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation.class);
        assertThatThrownBy(() -> determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, 1))
                .isInstanceOf(DomainException.class);
        assertThat(fakes.refunds).isEmpty();
    }

    @Test
    void aReservationOfAnotherTenantLooksMissingAndNoOtherOriginIsDetermined() {
        assertThatThrownBy(() -> determine.determine(UUID.randomUUID(), reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, null))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.CUSTOMER_CANCELLATION, null))
                .isInstanceOf(DomainException.class);
        assertThat(fakes.refunds).isEmpty();
    }
}
