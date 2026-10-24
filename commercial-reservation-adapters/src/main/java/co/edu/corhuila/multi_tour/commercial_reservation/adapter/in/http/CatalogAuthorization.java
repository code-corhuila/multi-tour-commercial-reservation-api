package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CollaboratorEditingPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

public final class CatalogAuthorization implements HandlerInterceptor {
    private final CollaboratorEditingPolicy collaboratorEditing;

    public CatalogAuthorization(CollaboratorEditingPolicy collaboratorEditing) {
        this.collaboratorEditing = collaboratorEditing;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var principal = (AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        if (principal.tenantId() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (principal.roles().contains("TENANT_ADMINISTRATOR")) return true;
        boolean collaborator = principal.roles().contains("OPERATIONAL_COLLABORATOR");
        boolean read = "GET".equals(request.getMethod());
        // The enabled Collaborator may update an item (RF-004); which fields is checked once the body is bound (RN-04).
        boolean enabledUpdate = "PATCH".equals(request.getMethod())
                && collaboratorEditing.allowsDescriptiveUpdates(principal.tenantId());
        // Deny access before argument resolution so malformed bodies cannot disclose validation to other roles.
        if (!(collaborator && (read || enabledUpdate))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return true;
    }
}
