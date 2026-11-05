package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.Optional;

// HU-COM-015 RN-02, plan D-02: which fact of the payment family notifies the customer. A pure function: no HTTP, no SQL, no mail. The refund, the
// credit balance and the rescheduling notices wait for the events of HU-COM-016, 017 and 018 (spec DP-15-04) and are not mapped here.
public final class NoticePolicy {

    private NoticePolicy() {
    }

    // reviewedProof tells a validated payment born from a proof that was reviewed (notifies) from cash recorded by staff (does not, DP-15-03).
    public static Optional<NoticeType> of(PaymentEvent event, boolean reviewedProof) {
        return switch (event) {
            case PaymentValidated validated -> reviewedProof ? Optional.of(NoticeType.PROOF_APPROVED) : Optional.empty();
            case PaymentRejected rejected -> Optional.of(NoticeType.PROOF_REJECTED);
            case ReservationConfirmed confirmed -> Optional.of(NoticeType.RESERVATION_CONFIRMED);
            case ReservationCancelled cancelled -> switch (cancelled.cause()) {
                case PAYMENT_EXPIRY -> Optional.of(NoticeType.RESERVATION_CANCELLED_NON_PAYMENT);
                case NOT_ATTRIBUTABLE_TO_CUSTOMER -> Optional.of(NoticeType.DEPARTURE_CANCELLED);
                case CUSTOMER_REQUEST -> Optional.empty();
            };
            default -> Optional.empty();
        };
    }
}
