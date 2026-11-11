package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// The role gate of the payment routes, before the body is bound, so a refused caller learns no validation details.
public final class PaymentHttpAccess implements HandlerInterceptor {

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var actor = PaymentActors.of((AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE));
        if ("POST".equals(request.getMethod())) PaymentAuthorization.requireRegister(actor);
        else PaymentAuthorization.requireReadPayments(actor);
        return true;
    }
}
