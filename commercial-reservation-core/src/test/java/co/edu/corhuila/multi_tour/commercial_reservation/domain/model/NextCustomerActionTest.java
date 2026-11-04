package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

// T-03 of HU-COM-014: AC-14.8, RN-06. The service, not the reader, says what the Final Customer may do next (DP-14-03 table, in order).
class NextCustomerActionTest {

    private static final Cents BALANCE = new Cents(150_000), ZERO = new Cents(0);

    private static NextCustomerAction of(ReservationStatus status, Cents balance, ValidationStatus proof, boolean deadlineHolds, boolean proposalOpen) {
        return NextCustomerAction.of(status, balance, Optional.ofNullable(proof), deadlineHolds, proposalOpen);
    }

    @Test
    void continueActionFollowsTheTableOfCases() {
        assertThat(of(ReservationStatus.PENDING_PAYMENT, BALANCE, null, true, false)).isEqualTo(NextCustomerAction.CONTINUE_PAYMENT);
        assertThat(of(ReservationStatus.CONFIRMED, BALANCE, null, true, false)).isEqualTo(NextCustomerAction.CONTINUE_PAYMENT);
        assertThat(of(ReservationStatus.CONFIRMED, BALANCE, ValidationStatus.APPROVED, true, false)).isEqualTo(NextCustomerAction.CONTINUE_PAYMENT);
        assertThat(of(ReservationStatus.PENDING_PAYMENT, BALANCE, ValidationStatus.REJECTED, true, false)).isEqualTo(NextCustomerAction.PRESENT_NEW_RECEIPT);
        assertThat(of(ReservationStatus.CONFIRMED, BALANCE, ValidationStatus.REJECTED, true, false)).isEqualTo(NextCustomerAction.PRESENT_NEW_RECEIPT);
        assertThat(of(ReservationStatus.CONFIRMED, ZERO, ValidationStatus.APPROVED, true, false)).isEqualTo(NextCustomerAction.NONE);
        assertThat(of(ReservationStatus.IN_EXECUTION, BALANCE, null, true, false)).isEqualTo(NextCustomerAction.NONE);
    }

    @Test
    void aClosedReservationOffersNoActionWhateverTheRest() {
        for (ReservationStatus closed : new ReservationStatus[] {ReservationStatus.CANCELLED, ReservationStatus.COMPLETED}) {
            for (ValidationStatus proof : new ValidationStatus[] {null, ValidationStatus.REJECTED, ValidationStatus.APPROVED}) {
                assertThat(of(closed, BALANCE, proof, true, false)).isEqualTo(NextCustomerAction.NONE);
            }
        }
    }

    @Test
    void anOpenProposalOrAProofInValidationOffersNoAction() {
        assertThat(of(ReservationStatus.PENDING_PAYMENT, BALANCE, ValidationStatus.IN_VALIDATION, true, false)).isEqualTo(NextCustomerAction.NONE);
        assertThat(of(ReservationStatus.CONFIRMED, BALANCE, ValidationStatus.IN_VALIDATION, true, false)).isEqualTo(NextCustomerAction.NONE);
        assertThat(of(ReservationStatus.PENDING_PAYMENT, BALANCE, null, true, true)).isEqualTo(NextCustomerAction.NONE);
        assertThat(of(ReservationStatus.CONFIRMED, BALANCE, ValidationStatus.REJECTED, true, true)).isEqualTo(NextCustomerAction.NONE);
    }

    @Test
    void aRejectedProofAfterTheDeadlineDoesNotOfferANewReceipt() {
        assertThat(of(ReservationStatus.PENDING_PAYMENT, BALANCE, ValidationStatus.REJECTED, false, false)).isNotEqualTo(NextCustomerAction.PRESENT_NEW_RECEIPT);
        assertThat(of(ReservationStatus.CANCELLED, BALANCE, ValidationStatus.REJECTED, false, false)).isEqualTo(NextCustomerAction.NONE);
    }
}
