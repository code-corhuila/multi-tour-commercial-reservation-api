package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

public record DocumentNumber(String value) {
    public static DocumentNumber of(String raw) {
        return new DocumentNumber(raw);
    }
}
