package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.List;

// AC-07.2: every missing mandatory datum is named at once, never one per attempt.
public final class MissingReservationDataException extends DomainException {
    private final List<String> fields;

    public MissingReservationDataException(List<String> fields) {
        super(String.join(",", fields), "reservation data is missing: " + String.join(", ", fields));
        this.fields = List.copyOf(fields);
    }

    public List<String> fields() {
        return fields;
    }
}
