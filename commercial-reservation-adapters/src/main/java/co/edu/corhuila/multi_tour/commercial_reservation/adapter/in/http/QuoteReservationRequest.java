package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingReservationDataException;
import java.util.ArrayList;
import java.util.UUID;

// Closed request (RN-13): the strict mapper refuses any value, tenant or date. Boxed values keep absence so each missing field is named.
public record QuoteReservationRequest(UUID serviceId, UUID salidaId, Integer people) {

    public void requireComplete() {
        var missing = new ArrayList<String>();
        if (serviceId == null) missing.add("serviceId");
        if (salidaId == null) missing.add("salidaId");
        if (people == null) missing.add("people");
        if (!missing.isEmpty()) throw new MissingReservationDataException(missing);
    }
}
