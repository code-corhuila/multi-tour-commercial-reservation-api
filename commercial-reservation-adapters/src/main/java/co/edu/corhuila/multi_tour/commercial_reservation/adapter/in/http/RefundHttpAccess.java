package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// The role gate of the public refund routes, before the body and any load (AC-16.3, DP-04): the Administrator and the Collaborator read, the Administrator alone writes.
public final class RefundHttpAccess implements HandlerInterceptor {
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var principal = (AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        boolean administrator = principal.roles().contains("TENANT_ADMINISTRATOR");
        boolean reader = administrator || principal.roles().contains("OPERATIONAL_COLLABORATOR");
        if (!("POST".equals(request.getMethod()) ? administrator : reader)) throw new PaymentAccessDeniedException();
        return true;
    }
}
