package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Default adapter: a structured log line with no sensitive data. The outbox event PlatformExceptionalAccessUsed replaces it once its name and payload are confirmed by the Project Lead.
public class LoggingExceptionalAccessAudit implements ExceptionalAccessAudit {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingExceptionalAccessAudit.class);

    @Override
    public void used(UUID authorizationId, String access, String area, String method, String routeTemplate) {
        LOG.info("exceptional access used authorizationId={} access={} area={} method={} route={}", authorizationId, access, area, method, routeTemplate);
    }
}
