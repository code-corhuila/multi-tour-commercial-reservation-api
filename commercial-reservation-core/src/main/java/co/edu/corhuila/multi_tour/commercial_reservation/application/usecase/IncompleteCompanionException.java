package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import java.util.List;

// AC-07.11: a companion lacks the data of a high-risk tour; the answer names its position and the fields, never the values.
public final class IncompleteCompanionException extends RuntimeException {
    private final int position;
    private final List<String> fields;

    public IncompleteCompanionException(int position, List<String> fields) {
        super("companion " + position + " is incomplete: " + String.join(", ", fields));
        this.position = position;
        this.fields = List.copyOf(fields);
    }

    public int position() {
        return position;
    }

    public List<String> fields() {
        return fields;
    }
}
