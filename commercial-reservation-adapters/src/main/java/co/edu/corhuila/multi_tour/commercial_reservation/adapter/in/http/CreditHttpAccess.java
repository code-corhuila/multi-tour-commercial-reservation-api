package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// The role gate of the credit balance routes, before the body and any load (AC-17.14, DP-03): the Administrator, the Collaborator and the Final Customer write; the
// Manager, the Accountant and the Analyst only read, so they are refused on every write.
public final class CreditHttpAccess implements HandlerInterceptor {
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var principal = (AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        boolean writer = principal.roles().stream().anyMatch(role -> role.equals("TENANT_ADMINISTRATOR") || role.equals("OPERATIONAL_COLLABORATOR")
                || role.equals("FINAL_CUSTOMER"));
        boolean reader = writer || principal.roles().stream().anyMatch(role -> role.equals("MANAGER") || role.equals("ACCOUNTANT") || role.equals("ANALYST"));
        if (!("POST".equals(request.getMethod()) ? writer : reader)) throw new PaymentAccessDeniedException();
        return true;
    }
}
