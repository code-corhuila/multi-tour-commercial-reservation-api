package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

// What this service keeps about a tenant of identity-audit (ADR-004 D5). It only moves forward by occurredAt (RN-TSC-03),
// each event type sets its own part (RN-TSC-04) and the identifier never changes once kept (RN-TSC-05).
public record TenantStatusCopy(UUID tenantId, String identifier, TenantStatus status, String timeZone, String country,
        Instant lastEventAt) {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

    // The copy a complete event creates, whatever its type; an incomplete one cannot create it (DP-TSC-12).
    public static TenantStatusCopy from(TenantEvent event) {
        if (event.identifier() == null || event.identifier().length() < 3 || event.identifier().length() > 40
                || !IDENTIFIER.matcher(event.identifier()).matches()) {
            throw new DomainException("identifier", "identifier must be lowercase kebab-case, 3 to 40 characters");
        }
        return new TenantStatusCopy(event.tenantId(), event.identifier(), statusOf(event, null),
                zone(event.timeZone()), country(event.country()), event.occurredAt());
    }

    // The next copy, or empty when the event is not strictly newer than the last one applied (DP-TSC-06).
    public Optional<TenantStatusCopy> apply(TenantEvent event) {
        if (!event.occurredAt().isAfter(lastEventAt)) {
            return Optional.empty();
        }
        boolean localization = event.type() == TenantEventType.TENANT_LOCALIZATION_CHANGED;
        boolean created = event.type() == TenantEventType.TENANT_CREATED;
        String nextZone = (localization || created) && event.timeZone() != null ? zone(event.timeZone()) : timeZone;
        String nextCountry = (localization || created) && event.country() != null ? country(event.country()) : country;
        return Optional.of(new TenantStatusCopy(tenantId, identifier, statusOf(event, status), nextZone, nextCountry,
                event.occurredAt()));
    }

    // The status of inactivation and reactivation comes from the type, never from the payload.
    private static TenantStatus statusOf(TenantEvent event, TenantStatus kept) {
        return switch (event.type()) {
            case TENANT_INACTIVATED -> TenantStatus.INACTIVE;
            case TENANT_REACTIVATED -> TenantStatus.ACTIVE;
            case TENANT_CREATED -> required(event.status() != null ? event.status() : kept);
            case TENANT_LOCALIZATION_CHANGED -> required(kept != null ? kept : event.status());
        };
    }

    private static TenantStatus required(TenantStatus status) {
        if (status == null) {
            throw new DomainException("tenantStatus", "tenantStatus is required to create the copy");
        }
        return status;
    }

    private static String zone(String value) {
        try {
            if (value == null || value.length() > 64) throw new DateTimeException("length");
            ZoneId.of(value);
            return value;
        } catch (DateTimeException invalid) {
            throw new DomainException("timeZone", "timeZone must be an IANA time zone, up to 64 characters");
        }
    }

    private static String country(String value) {
        if (value == null || !COUNTRY.matcher(value).matches()) {
            throw new DomainException("country", "country must be two capital letters (ISO 3166-1 alpha-2)");
        }
        return value;
    }
}
