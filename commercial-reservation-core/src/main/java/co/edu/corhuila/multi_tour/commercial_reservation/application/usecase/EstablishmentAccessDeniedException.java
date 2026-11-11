package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

public final class EstablishmentAccessDeniedException extends RuntimeException {
    public EstablishmentAccessDeniedException() { super("access is forbidden"); }
}
