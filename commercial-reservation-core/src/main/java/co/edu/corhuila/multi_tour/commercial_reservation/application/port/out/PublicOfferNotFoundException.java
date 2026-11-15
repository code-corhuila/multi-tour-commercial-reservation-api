package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

// One not found for an unknown or inactive tenant, an item of another tenant, an inactive item or a wrong kind (RN-07, RN-09).
public class PublicOfferNotFoundException extends RuntimeException {
    public PublicOfferNotFoundException() { super("not found"); }
}
