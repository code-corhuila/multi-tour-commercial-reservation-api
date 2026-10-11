package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

public final class AssociatedEstablishmentNotFoundException extends RuntimeException {
    public AssociatedEstablishmentNotFoundException() { super("association not found"); }
}
