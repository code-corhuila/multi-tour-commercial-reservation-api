package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExceptionalAccessUse;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExceptionalAccessAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// A structured log line with no sensitive data; the default of the filter when no other audit is given, and the line the outbox audit also writes.
public class LoggingExceptionalAccessAudit implements ExceptionalAccessAudit {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingExceptionalAccessAudit.class);

    @Override
    public void used(ExceptionalAccessUse use) {
        LOG.info("exceptional access used authorizationId={} access={} area={} method={} route={}", use.authorizationId(), use.access(), use.area(), use.method(), use.routeTemplate());
    }
}
