package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-017 DP-D17-2 (PROPOSED default): where a credit balance came from, as wire codes. The first three are the kinds of a decision that can end as the
// 100 % credit; PAYMENT_EXCESS is the excess of a validated payment over the pending value.
public enum CreditOrigin {
    PENDING_TREATMENT, MODIFICATION_EXCESS, CUSTOMER_CANCELLATION, PAYMENT_EXCESS
}
