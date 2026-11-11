package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.UUID;

// Port: the audit of each use of an exceptional access token (HU-SEC-007 G-SEC9-1, AC7). Never receives data of the request, only who authorized it and which route was reached.
public interface ExceptionalAccessAudit {
    void used(UUID authorizationId, String access, String area, String method, String routeTemplate);
}
