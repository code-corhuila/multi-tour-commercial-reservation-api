package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

public final class ReservationAccessDeniedException extends RuntimeException {
    public ReservationAccessDeniedException() { super("access is forbidden"); }
}
