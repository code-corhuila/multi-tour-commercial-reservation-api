package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

public enum EstablishmentType {
    HOTEL, RESTAURANT;

    public static EstablishmentType parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new DomainException("establishmentType", "establishmentType must be HOTEL or RESTAURANT");
        }
    }
}
