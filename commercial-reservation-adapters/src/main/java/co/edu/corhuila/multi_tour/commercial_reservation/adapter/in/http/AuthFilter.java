package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExceptionalAccessUse;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AccountSessionCutoffs;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExceptionalAccessAudit;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
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
    private final ExceptionalAccessAudit audit;
    private final Clock clock;
    private final AccountSessionCutoffs cutoffs;


    public AuthFilter(Rs256Verifier verifier, ApiErrorWriter errors, TenantStatusReader statuses, ExceptionalAccessAudit audit, Clock clock,
            AccountSessionCutoffs cutoffs) {
        this.cutoffs = cutoffs;
        this.clock = clock;
        this.audit = audit;
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
            if (revoked(principal)) {
                errors.write(request, response, ApiError.Code.UNAUTHORIZED, "authentication is required");
                return;
            }
            if (principal.restricted() && !RestrictedAccessPolicy.allows(request.getMethod(), request.getRequestURI().substring(request.getContextPath().length()), principal.roles())) {
                errors.write(request, response, ApiError.Code.FORBIDDEN, "the access is restricted to the work of an inactive tenant");
                return;
            }
            if (principal.exceptional() != null) {
                // HU-SEC-007 G-SEC9-1 (ADR-004 D5): only a read inside the scope; any write, internal route or route not listed is refused, and each use is audited with the authorizationId.
                String path = request.getRequestURI().substring(request.getContextPath().length());
                ExceptionalAccess access = principal.exceptional();
                if (!ExceptionalAccessPolicy.allows(request.getMethod(), path, access.scope())) {
                    errors.write(request, response, ApiError.Code.FORBIDDEN, "the exceptional access is read only and limited to its scope");
                    return;
                }
                audit.used(new ExceptionalAccessUse(access.authorizationId(), UUID.fromString(principal.sub()), principal.tenantId(), access.access(),
                        ExceptionalAccessPolicy.areaOf(request.getMethod(), path).orElseThrow(), request.getMethod(), ExceptionalAccessPolicy.template(path), clock.instant(),
                        (String) request.getAttribute(CorrelationFilter.TRACE_ATTRIBUTE)));
            }
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

    // A token issued before the cutoff of its account (ADR-004 D5, api spec 022). A SERVICE token or a subject that is not an account id never has one.
    // The lookup fails loudly: an exception here is the 500 of the catch below, never a token let through.
    private boolean revoked(AuthenticatedPrincipal principal) {
        if (principal.roles().contains("SERVICE")) return false;
        UUID account;
        try {
            account = UUID.fromString(principal.sub());
        } catch (IllegalArgumentException notAnAccount) {
            return false;
        }
        return cutoffs.find(account).filter(cutoff -> cutoff.refuses(principal.issuedAt())).isPresent();
    }

    // The receiving route is checked by its SERVICE role alone, because it must work for an inactive tenant.
    private boolean inactive(HttpServletRequest request, AuthenticatedPrincipal principal) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // The exceptional or emergency access reads only, so an inactive tenant does not close it (HU-SEC-007, Project Lead 2026-11-11).
        if (principal.tenantId() == null || principal.restricted() || principal.exceptional() != null
                || path.matches("/api/v1/commercial-reservation/internal/tenants/[^/]+/events") || "/api/v1/commercial-reservation/internal/events".equals(path)) {
            return false;
        }
        Optional<TenantStatus> status = statuses.statusOf(principal.tenantId());
        if (status.isEmpty()) {
            LOG.warn("No tenant status copy yet, tenantId={}", principal.tenantId());
        }
        return status.filter(TenantStatus.INACTIVE::equals).isPresent();
    }
}
