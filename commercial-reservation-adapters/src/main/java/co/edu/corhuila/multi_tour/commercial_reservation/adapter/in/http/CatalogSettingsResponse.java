package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogSettings;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

// updatedAt and updatedBy are written as null before the first change: the contract requires both keys.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CatalogSettingsResponse(boolean collaboratorEditing, String updatedAt, UUID updatedBy) {
    public static CatalogSettingsResponse from(CatalogSettings settings) {
        return new CatalogSettingsResponse(settings.collaboratorEditing(),
                settings.updatedAt() == null ? null : settings.updatedAt().toString(), settings.updatedBy());
    }
}
