package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// AC-11.7, RN-05: one contact with the customer of a reservation pending payment. It is appended and never edited or deleted, it
// records who and when from the token and the clock, and it never changes the deadline. The note is free text: stored, never logged
// and never put in an event (DP-03, DP-07).
public record PaymentFollowup(UUID followupId, UUID tenantId, UUID reservationId, UUID contactedBy, Instant contactedAt, String note) {

    public static final int MAX_NOTE = 1000;

    public PaymentFollowup {
        if (followupId == null || tenantId == null || reservationId == null || contactedBy == null || contactedAt == null)
            throw new DomainException("followup", "the follow-up data is incomplete");
        if (note == null || note.isBlank()) throw new DomainException("note", "note is required");
        note = note.strip();
        if (note.length() > MAX_NOTE) throw new DomainException("note", "note is longer than " + MAX_NOTE + " characters");
    }
}
