package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// The role gate of the rescheduling writes, before the body is bound and before any load (AC-18.15, DP-01): the proposal, the outside answer and the declaration of no alternative belong to the
// Administrator and the Collaborator; the answer of the customer belongs to the Final Customer alone. Everyone else is refused, so a refused caller learns neither whether the episode exists
// nor which fields are invalid. The reads are not gated here: the use case judges them.
public final class ReschedulingHttpAccess implements HandlerInterceptor {
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) return true;
        var principal = (AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        boolean allowed = request.getRequestURI().endsWith("/answer") ? principal.roles().contains("FINAL_CUSTOMER")
                : principal.roles().contains("TENANT_ADMINISTRATOR") || principal.roles().contains("OPERATIONAL_COLLABORATOR");
        if (!allowed) throw new PaymentAccessDeniedException();
        return true;
    }
}
