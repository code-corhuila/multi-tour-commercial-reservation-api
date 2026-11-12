package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** The bearer token of the request being served, so a read of another domain forwards the token of the person (ADR-004 D5). */
public final class CurrentRequestToken {

    private static final String PREFIX = "Bearer ";

    private CurrentRequestToken() {}

    public static Optional<String> bearer() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return Optional.empty();
        }
        HttpServletRequest request = servlet.getRequest();
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX) || header.length() == PREFIX.length()) {
            return Optional.empty();
        }
        return Optional.of(header.substring(PREFIX.length()));
    }
}
