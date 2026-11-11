package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.List;

// AC-09.11: every missing datum of a payment is named at once, never one per attempt.
public final class MissingPaymentDataException extends DomainException {
    private final List<String> fields;

    public MissingPaymentDataException(List<String> fields) {
        super(String.join(",", fields), "payment data is missing: " + String.join(", ", fields));
        this.fields = List.copyOf(fields);
    }

    public List<String> fields() {
        return fields;
    }
}
