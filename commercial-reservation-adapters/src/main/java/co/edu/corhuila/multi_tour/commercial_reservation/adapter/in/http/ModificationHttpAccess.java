package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CancellationAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// The role gate of the modification routes (AC-12.8), before the body is bound and before any load, so a refused caller learns neither
// whether the reservation exists nor which fields are invalid. Quote and write: Administrator and Collaborator; the history: the staff roles.
public final class ModificationHttpAccess implements HandlerInterceptor {

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var actor = PaymentActors.of((AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE));
        if ("GET".equals(request.getMethod())) CancellationAuthorization.requireRead(actor);
        else CancellationAuthorization.requireCancel(actor);
        return true;
    }
}
