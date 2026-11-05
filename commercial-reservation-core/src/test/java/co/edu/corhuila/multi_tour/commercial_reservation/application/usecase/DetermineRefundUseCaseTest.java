package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot.State;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03 of HU-COM-016 (-api): AC-16.7; DP-05, DP-15. The determination received from the domain of operations: for a cause not attributable the service
// computes 100 percent of the paid and validated value and refuses an amount from outside; for a personal emergency the amount of the policy is required.
class DetermineRefundUseCaseTest {

    private final RefundFakes fakes = new RefundFakes();
    private final UUID reservation = UUID.randomUUID();
    private final RefundAutomationUseCase automation = new RefundAutomationUseCase(fakes.decisionRepository, fakes.refundRepository, fakes.calendar,
            fakes.outbox, fakes.ids, fakes.transaction, RefundFakes.CLOCK);
    private final DetermineRefundUseCase determine = new DetermineRefundUseCase(
            (tenant, id) -> tenant.equals(RefundFakes.TENANT) && id.equals(reservation)
                    ? Optional.of(new ReservationSnapshot(tenant, id, State.CANCELLED, new Cents(300_000), new Cents(180_000), 0)) : Optional.empty(),
            automation);

    @Test
    void aCauseNotAttributableRefundsTheWholePaidValueAndAnAmountFromOutsideIsRefused() {
        RefundAuthorization refund = determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, null);

        assertThat(refund.authorizedAmount()).isEqualTo(new Cents(180_000));
        assertThat(refund.authorizedBy()).isNull();
        assertThat(fakes.events).containsExactly(refund);
        assertThatThrownBy(() -> determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, 1L))
                .isInstanceOf(DomainException.class).extracting("field").isEqualTo("amountCents");
    }

    @Test
    void aPersonalEmergencyTakesTheAmountOfThePolicyAndRequiresIt() {
        RefundAuthorization refund = determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, 50_000L);

        assertThat(refund.authorizedAmount()).isEqualTo(new Cents(50_000));
        assertThatThrownBy(() -> determine.determine(RefundFakes.TENANT, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, null))
                .isInstanceOf(DomainException.class).extracting("field").isEqualTo("amountCents");
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
