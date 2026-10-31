package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

// The fact that a Final Customer verified the e-mail of an account (ADR-004 D5; domain-events.md: tenant, account, normalized
// e-mail, time). The e-mail is trimmed and lower-cased like the one of a customer record, so two spellings are one address.
public record AccountEmailVerified(UUID eventId, UUID tenantId, UUID accountId, String email, Instant occurredAt) {
    public AccountEmailVerified {
        if (eventId == null) throw new DomainException("eventId", "eventId is required");
        if (tenantId == null) throw new DomainException("tenantId", "tenantId is required");
        if (accountId == null) throw new DomainException("accountId", "accountId is required");
        if (email == null || email.isBlank()) throw new DomainException("email", "email is required");
        email = email.strip().toLowerCase(Locale.ROOT);
    }
}
