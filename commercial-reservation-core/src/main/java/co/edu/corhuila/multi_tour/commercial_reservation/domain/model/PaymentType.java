package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// Expected payment type, wire codes of DP-08 (PROPOSED): full payment or deposit.
public enum PaymentType {
    FULL_PAYMENT, DEPOSIT;

    public static PaymentType fromCode(String code) {
        for (PaymentType type : values()) {
            if (type.name().equals(code)) return type;
        }
        throw new DomainException("paymentType", "paymentType must be one of FULL_PAYMENT, DEPOSIT");
    }
}
