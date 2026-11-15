package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ClientNoticeContent;
import java.util.UUID;

public interface ReadClientNoticeContent {

    ClientNoticeContent read(UUID tenantId, UUID eventId);
}
