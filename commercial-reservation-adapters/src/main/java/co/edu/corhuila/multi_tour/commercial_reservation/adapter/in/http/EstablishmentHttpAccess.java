package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.EstablishmentAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.EstablishmentAccessDeniedException;
import jakarta.servlet.http.*;
import java.util.UUID;
import org.springframework.web.servlet.HandlerInterceptor;

public final class EstablishmentHttpAccess implements HandlerInterceptor {
    public static EstablishmentActor actor(AuthenticatedPrincipal principal) {
        UUID account;
        try {
            account = UUID.fromString(principal.sub());
            if (!account.toString().equalsIgnoreCase(principal.sub())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new InvalidTokenException(); }
        for (var role : EstablishmentActor.Role.values()) {
            if (principal.roles().contains(role.name())) return new EstablishmentActor(principal.tenantId(), account, role);
        }
        throw new EstablishmentAccessDeniedException();
    }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Permission precedes multipart argument resolution, so refused callers learn no validation details.
        var principal = (AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        EstablishmentAuthorization.require(actor(principal), !"GET".equals(request.getMethod()));
        return true;
    }
}
