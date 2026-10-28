package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.Locale;

// Normalized once (plan D-05): trimmed and upper-cased, so two spellings are one document.
// TODO(plan D-05): which characters beyond spaces are legal is NO DEFINIDO; equality is T-03.
public record DocumentNumber(String value) {
    public DocumentNumber {
        if (value == null || value.isBlank()) throw new DomainException("documentNumber", "documentNumber is required");
        value = value.strip().toUpperCase(Locale.ROOT);
    }

    public static DocumentNumber of(String raw) {
        return new DocumentNumber(raw);
    }
}
