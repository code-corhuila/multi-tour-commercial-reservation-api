package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleAccessDeniedException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.web.servlet.HandlerInterceptor;

public final class DiscountRuleHttpAccess implements HandlerInterceptor {
    // Maps the verified token to the actor of the use cases; the tenant is only ever the token claim.
    public static DiscountRuleActor actor(AuthenticatedPrincipal principal) {
        UUID account;
        try {
            account = UUID.fromString(principal.sub());
            if (!account.toString().equalsIgnoreCase(principal.sub())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new InvalidTokenException(); }
        for (var role : DiscountRuleActor.Role.values()) {
            if (principal.roles().contains(role.name())) return new DiscountRuleActor(principal.tenantId(), account, role);
        }
        throw new DiscountRuleAccessDeniedException();
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Permission precedes body binding, so a refused caller learns no validation details.
        DiscountRuleAuthorization.requireWrite(actor((AuthenticatedPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE)));
        return true;
    }
}
