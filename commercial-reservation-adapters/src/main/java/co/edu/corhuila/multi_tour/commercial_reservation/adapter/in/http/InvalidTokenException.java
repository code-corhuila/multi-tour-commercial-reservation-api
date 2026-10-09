package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("invalid access token");
    }
}
