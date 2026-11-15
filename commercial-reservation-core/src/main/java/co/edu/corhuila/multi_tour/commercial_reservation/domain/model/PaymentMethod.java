package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// Expected payment medium, wire codes of DP-08 (PROPOSED): bank transfer or cash.
public enum PaymentMethod {
    TRANSFER, CASH;

    public static PaymentMethod fromCode(String code) {
        for (PaymentMethod method : values()) {
            if (method.name().equals(code)) return method;
        }
        throw new DomainException("paymentMethod", "paymentMethod must be one of TRANSFER, CASH");
    }
}
