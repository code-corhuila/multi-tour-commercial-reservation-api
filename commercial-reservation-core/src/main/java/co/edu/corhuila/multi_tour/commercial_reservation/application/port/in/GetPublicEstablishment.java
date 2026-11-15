package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicEstablishment;
import java.util.UUID;

public interface GetPublicEstablishment {
    PublicEstablishment get(String tenantIdentifier, UUID associationId);
}
