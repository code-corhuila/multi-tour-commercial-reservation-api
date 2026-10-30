package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReservationAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public final class ReservationHttpAccess implements HandlerInterceptor {

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Permission precedes body binding, so a refused caller learns no validation details (RN-10).
        var actor = CustomerHttpAccess.actor((AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE));
        if ("POST".equals(request.getMethod())) ReservationAuthorization.requireWrite(actor);
        else if (request.getRequestURI().endsWith("/departures")) ReservationAuthorization.requireDepartures(actor);
        else ReservationAuthorization.requireRead(actor);
        return true;
    }
}
