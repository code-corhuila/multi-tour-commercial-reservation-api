package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerAccessDeniedException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.web.servlet.HandlerInterceptor;

public final class CustomerHttpAccess implements HandlerInterceptor {
    // Maps the verified token to the actor of the use cases; the tenant and the permissions are only the token's.
    public static CustomerActor actor(AuthenticatedPrincipal principal) {
        UUID account;
        try {
            account = UUID.fromString(principal.sub());
            if (!account.toString().equalsIgnoreCase(principal.sub())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new InvalidTokenException(); }
        for (var role : CustomerActor.Role.values()) {
            if (principal.roles().contains(role.name()))
                return new CustomerActor(principal.tenantId(), account, role, principal.permissions());
        }
        throw new CustomerAccessDeniedException();
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Permission precedes body binding, so a refused caller learns no validation details.
        var actor = actor((AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE));
        if ("POST".equals(request.getMethod())) CustomerAuthorization.requireWrite(actor);
        else if (request.getRequestURI().endsWith("/sensitive-data")) CustomerAuthorization.requireSensitiveRead(actor);
        else CustomerAuthorization.requireRead(actor);
        return true;
    }
}
