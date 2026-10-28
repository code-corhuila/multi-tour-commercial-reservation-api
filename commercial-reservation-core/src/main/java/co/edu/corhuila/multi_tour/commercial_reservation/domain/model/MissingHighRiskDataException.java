package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.List;

public final class MissingHighRiskDataException extends DomainException {
    private final List<String> fields;

    public MissingHighRiskDataException(List<String> fields) {
        super(String.join(",", fields), "high-risk data is missing: " + String.join(", ", fields));
        this.fields = List.copyOf(fields);
    }

    // AC-05.2: the answer names each missing field.
    public List<String> fields() {
        return fields;
    }
}
