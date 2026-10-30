package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import java.util.UUID;

// The tenant behind the identifier of a public path (RN-TSC-07): a malformed identifier is a validation error, an unknown
// or inactive tenant is the shared not found.
public interface ResolvePublicTenant {
    UUID resolve(String identifier);
}
