package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ActionMarker;
import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

// HU-COM-020 AC-20.13, DP-05 (PROPOSED default): reads the authenticated principal of the request in course; a restricted one marks the action with RESTRICTED_ACCESS. A thread with no request (a worker job,
// a hook) has no marker.
public final class RestrictedActionMarker implements ActionMarker {

    @Override
    public Optional<String> reason() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request == null) return Optional.empty();
        return request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof AuthenticatedPrincipal principal && principal.restricted()
                ? Optional.of(RESTRICTED_ACCESS) : Optional.empty();
    }
}
