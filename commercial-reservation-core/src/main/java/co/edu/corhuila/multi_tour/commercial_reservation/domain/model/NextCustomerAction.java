package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.Optional;

// HU-COM-014 AC-14.8, RN-06: the action the Final Customer may take next on a reservation. The service decides it from state; the
// reader only displays it. The cases are the ordered table of DP-14-03 (PROPOSED default): the first one that applies wins.
public enum NextCustomerAction {
    CONTINUE_PAYMENT, PRESENT_NEW_RECEIPT, NONE;

    // deadlineHolds is true when the payment deadline has not expired, or when the reservation has no open deadline (G-DL).
    public static NextCustomerAction of(ReservationStatus status, Cents pendingBalance, Optional<ValidationStatus> latestProof,
            boolean deadlineHolds, boolean proposalOpen) {
        if (status == ReservationStatus.CANCELLED || status == ReservationStatus.COMPLETED) return NONE;
        if (latestProof.filter(proof -> proof == ValidationStatus.IN_VALIDATION).isPresent()) return NONE;
        if (proposalOpen) return NONE;
        if (latestProof.filter(proof -> proof == ValidationStatus.REJECTED).isPresent() && deadlineHolds) return PRESENT_NEW_RECEIPT;
        boolean payable = status == ReservationStatus.PENDING_PAYMENT || status == ReservationStatus.CONFIRMED;
        if (payable && pendingBalance.amount() > 0) return CONTINUE_PAYMENT;
        return NONE;
    }
}
