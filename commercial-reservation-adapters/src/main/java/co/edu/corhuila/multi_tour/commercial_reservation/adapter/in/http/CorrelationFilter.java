package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class CorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String TRACE_ATTRIBUTE = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String trace = request.getHeader(HEADER);
        if (trace == null || !trace.matches("[A-Za-z0-9_.-]{1,128}")) trace = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ATTRIBUTE, trace);
        response.setHeader(HEADER, trace);
        MDC.put("traceId", trace);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
