package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExceptionalAccessUse;

// Port: the audit of each use of an exceptional access token (HU-SEC-007 G-SEC9-1, AC7). It never receives data of the request or of the response, only who authorized the access and which route was reached.
// An implementation that cannot write the audit throws, and the use is not served.
public interface ExceptionalAccessAudit {
    void used(ExceptionalAccessUse use);
}
