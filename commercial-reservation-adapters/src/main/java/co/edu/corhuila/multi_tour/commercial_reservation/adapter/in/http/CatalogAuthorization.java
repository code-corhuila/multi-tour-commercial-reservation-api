package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

public final class CatalogAuthorization implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var principal = (AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        if (principal.tenantId() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        boolean read = "GET".equals(request.getMethod());
        // Deny access before argument resolution so malformed bodies cannot disclose validation to other roles.
        if (!principal.roles().contains("TENANT_ADMINISTRATOR")
                && !(read && principal.roles().contains("OPERATIONAL_COLLABORATOR"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return true;
    }
}
