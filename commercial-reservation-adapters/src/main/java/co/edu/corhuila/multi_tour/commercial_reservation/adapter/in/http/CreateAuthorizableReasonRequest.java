package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;

public record CreateAuthorizableReasonRequest(String text) {
    public String validText() {
        if (text == null || text.isBlank()) throw new DomainException("text", "text is required");
        if (text.trim().length() > 255) throw new DomainException("text", "must have at most 255 characters");
        return text;
    }
}
