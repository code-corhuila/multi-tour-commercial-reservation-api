package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

// No user and no tenant: both come from the token. Boolean and not boolean, so a missing flag is told apart from false.
public record SetCatalogSettingsRequest(Boolean collaboratorEditing) {
}
