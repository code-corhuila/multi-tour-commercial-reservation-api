package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PublicOfferNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.UUID;
import java.util.regex.Pattern;

// The rules every public read shares: the identifier is checked before any query (plan D-03) and an unknown or
// inactive tenant is the same not found as a missing item (RN-07, DP-02).
final class PublicTenants {

    // Lowercase kebab-case (ADR-004 D5) or the interim UUID; 64 characters is PROPOSED, no document gives a maximum.
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MAX_IDENTIFIER_LENGTH = 64;

    private PublicTenants() {
    }

    static UUID require(TenantDirectory directory, String identifier) {
        if (identifier == null || identifier.length() > MAX_IDENTIFIER_LENGTH || !IDENTIFIER.matcher(identifier).matches()) {
            throw new PublicOfferNotFoundException();
        }
        return directory.resolve(identifier).filter(TenantRef::active).map(TenantRef::tenantId)
                .orElseThrow(PublicOfferNotFoundException::new);
    }

    static void requirePage(int page, int limit) {
        if (page < 1) throw new DomainException("page", "page must be at least 1");
        if (limit < 1 || limit > 100) throw new DomainException("limit", "limit must be between 1 and 100");
    }
}
