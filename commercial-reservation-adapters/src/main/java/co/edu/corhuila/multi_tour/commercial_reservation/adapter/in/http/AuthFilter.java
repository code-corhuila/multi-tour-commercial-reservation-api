package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthFilter extends OncePerRequestFilter {
    public static final String PRINCIPAL_ATTRIBUTE = "authenticatedPrincipal";
    private final Rs256Verifier verifier;
    private final ApiErrorWriter errors;

    public AuthFilter(Rs256Verifier verifier, ApiErrorWriter errors) {
        this.verifier = verifier;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "GET".equals(request.getMethod())
                && "/health".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new InvalidTokenException();
            request.setAttribute(PRINCIPAL_ATTRIBUTE, verifier.verify(authorization.substring(7)));
        } catch (InvalidTokenException failure) {
            errors.write(request, response, ApiError.Code.UNAUTHORIZED, "authentication is required");
            return;
        }
        chain.doFilter(request, response);
    }
}
