package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.util.UUID;

// commercialInformation travels as stored (DP-12); imageUrl is the existing public image route (AC-06.17).
public record PublicEstablishment(UUID associationId, EstablishmentType establishmentType, String commercialName,
        String commercialInformation, String imageUrl) {
}
