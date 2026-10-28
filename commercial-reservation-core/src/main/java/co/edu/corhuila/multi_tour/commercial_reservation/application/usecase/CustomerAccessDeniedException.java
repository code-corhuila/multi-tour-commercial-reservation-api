package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

public final class CustomerAccessDeniedException extends RuntimeException {
    public CustomerAccessDeniedException() { super("access is forbidden"); }
}
