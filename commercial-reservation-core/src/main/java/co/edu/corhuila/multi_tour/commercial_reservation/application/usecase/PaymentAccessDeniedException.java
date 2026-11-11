package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

public final class PaymentAccessDeniedException extends RuntimeException {
    public PaymentAccessDeniedException() { super("access is forbidden"); }
}
