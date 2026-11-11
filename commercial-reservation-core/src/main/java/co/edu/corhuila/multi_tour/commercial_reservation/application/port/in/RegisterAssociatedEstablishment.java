package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentRegistration;

public interface RegisterAssociatedEstablishment {
    EstablishmentRegistration register(EstablishmentActor actor, String key, Command command);
    record Command(String establishmentType, String commercialName, String commercialInformation, String imageReference) {}
}
