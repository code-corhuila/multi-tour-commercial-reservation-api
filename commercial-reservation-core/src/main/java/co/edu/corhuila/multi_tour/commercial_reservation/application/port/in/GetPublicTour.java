package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTourDetail;
import java.util.UUID;

public interface GetPublicTour {
    PublicTourDetail get(String tenantIdentifier, UUID serviceId);
}
