package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.Locale;

// Normalized once (plan D-05): trimmed and upper-cased, so two spellings are one document.
// Decision (spec 005 plan D-05, proposed): the characters beyond spaces that are legal are NO DEFINIDO, so none is refused. Equality is comparable().
public record DocumentNumber(String value) {
    public DocumentNumber {
        if (value == null || value.isBlank()) throw new DomainException("documentNumber", "documentNumber is required");
        value = value.strip().toUpperCase(Locale.ROOT);
    }

    // What two documents are compared by: the spacing inside a document never makes it another one ("CC 55" and "cc55").
    public String comparable() {
        return value.replaceAll("\\s+", "");
    }

    public static DocumentNumber of(String raw) {
        return new DocumentNumber(raw);
    }
}
