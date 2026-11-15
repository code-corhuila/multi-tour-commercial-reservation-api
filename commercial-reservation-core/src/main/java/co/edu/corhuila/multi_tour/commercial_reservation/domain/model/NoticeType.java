package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// The eight notices of RF-019 (HU-COM-015 api spec, "Notice types"): English wire codes, the Spanish text is the worker's. They are the codes the
// database accepts in the payload of a ClientNotified event.
public enum NoticeType {
    PROOF_APPROVED, PROOF_REJECTED, RESERVATION_CONFIRMED, RESERVATION_CANCELLED_NON_PAYMENT, REFUND_PROCESSED, CREDIT_BALANCE_RECORDED,
    DEPARTURE_CANCELLED, RESCHEDULING_PROPOSED
}
