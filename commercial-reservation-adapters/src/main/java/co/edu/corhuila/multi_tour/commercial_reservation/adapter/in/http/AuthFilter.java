package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthFilter extends OncePerRequestFilter {
    public static final String PRINCIPAL_ATTRIBUTE = "authenticatedPrincipal";
    private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);
    private final Rs256Verifier verifier;
    private final ApiErrorWriter errors;
    private final TenantStatusReader statuses;

    public AuthFilter(Rs256Verifier verifier, ApiErrorWriter errors, TenantStatusReader statuses) {
        this.verifier = verifier;
        this.errors = errors;
        this.statuses = statuses;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ("GET".equals(request.getMethod()) && "/health".equals(path)) || PublicPaths.allows(request.getMethod(), path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (PublicPaths.isPublicArea(request.getRequestURI().substring(request.getContextPath().length()))) {
            // A method or a path the public area does not list: the shared not found, never 401 (plan D-04).
            errors.write(request, response, ApiError.Code.NOT_FOUND, "resource not found");
            return;
        }
        String authorization = request.getHeader("Authorization");
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new InvalidTokenException();
            AuthenticatedPrincipal principal = verifier.verify(authorization.substring(7));
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            if (inactive(request, principal)) {
                errors.write(request, response, ApiError.Code.UNAUTHORIZED, "authentication is required");
                return;
            }
        } catch (InvalidTokenException failure) {
            errors.write(request, response, ApiError.Code.UNAUTHORIZED, "authentication is required");
            return;
        } catch (RuntimeException failure) {
            // A lookup that fails is never a token let through (plan D-05).
            LOG.error("Tenant status lookup failed", failure);
            errors.write(request, response, ApiError.Code.INTERNAL_ERROR, "an unexpected error occurred");
            return;
        }
        chain.doFilter(request, response);
    }

    // The receiving route is checked by its SERVICE role alone, because it must work for an inactive tenant.
    private boolean inactive(HttpServletRequest request, AuthenticatedPrincipal principal) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (principal.tenantId() == null || principal.restricted()
                || path.matches("/api/v1/commercial-reservation/internal/tenants/[^/]+/events")) {
            return false;
        }
        Optional<TenantStatus> status = statuses.statusOf(principal.tenantId());
        if (status.isEmpty()) {
            LOG.warn("No tenant status copy yet, tenantId={}", principal.tenantId());
        }
        return status.filter(TenantStatus.INACTIVE::equals).isPresent();
    }
}
