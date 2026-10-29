package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

public final class DiscountRuleAccessDeniedException extends RuntimeException {
    public DiscountRuleAccessDeniedException() { super("access is forbidden"); }
}
