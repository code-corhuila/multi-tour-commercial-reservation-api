package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentProofAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// The role gate of the review routes, before the body is bound. The list and the detail are for the reading roles; the decisions for
// whoever holds the permission; the file is judged by its use case because the owner of the reservation may read it too.
public final class PaymentProofHttpAccess implements HandlerInterceptor {

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var actor = PaymentActors.of((AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE));
        String path = request.getRequestURI();
        if (path.endsWith("/file")) return true;
        if ("POST".equals(request.getMethod())) PaymentProofAuthorization.requireDecide(actor);
        else PaymentProofAuthorization.requireRead(actor);
        return true;
    }
}
