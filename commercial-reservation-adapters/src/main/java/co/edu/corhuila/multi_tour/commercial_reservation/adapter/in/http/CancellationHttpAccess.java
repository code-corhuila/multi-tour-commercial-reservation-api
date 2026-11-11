package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CancellationAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// The role gate of the cancellation routes (AC-13.5), before the body is bound and before any load, so a refused caller learns neither
// whether the reservation exists nor which fields are invalid. Quote and cancel: Administrator and Collaborator; the record: the staff roles.
public final class CancellationHttpAccess implements HandlerInterceptor {

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var actor = PaymentActors.of((AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE));
        if ("GET".equals(request.getMethod()) && !request.getRequestURI().endsWith("/cancellation-quote")) CancellationAuthorization.requireRead(actor);
        else CancellationAuthorization.requireCancel(actor);
        return true;
    }
}
