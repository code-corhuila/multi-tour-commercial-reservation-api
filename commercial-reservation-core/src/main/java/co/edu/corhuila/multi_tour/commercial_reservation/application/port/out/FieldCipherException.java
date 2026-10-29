package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

/** Failure of the field cipher. Its message never carries a value or a key. */
public class FieldCipherException extends RuntimeException {

    public FieldCipherException(String message) {
        super(message);
    }
}
