package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.time.Instant;
import java.util.UUID;

/** The catalog settings of a tenant (RF-004). updatedAt and updatedBy are null until the first change. */
public record CatalogSettings(boolean collaboratorEditing, Instant updatedAt, UUID updatedBy) {

    /** What a tenant that never set anything has: the Operational Collaborator may not edit. */
    public static CatalogSettings defaults() {
        return new CatalogSettings(false, null, null);
    }
}
