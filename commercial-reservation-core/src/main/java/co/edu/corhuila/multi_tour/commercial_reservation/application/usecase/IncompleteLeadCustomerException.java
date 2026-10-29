package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import java.util.List;

// AC-07.21: the lead customer lacks data the creation needs; the answer names the fields, never their values.
public final class IncompleteLeadCustomerException extends RuntimeException {
    private final List<String> fields;

    public IncompleteLeadCustomerException(List<String> fields) {
        super("the lead customer is incomplete: " + String.join(", ", fields));
        this.fields = List.copyOf(fields);
    }

    public List<String> fields() {
        return fields;
    }
}
