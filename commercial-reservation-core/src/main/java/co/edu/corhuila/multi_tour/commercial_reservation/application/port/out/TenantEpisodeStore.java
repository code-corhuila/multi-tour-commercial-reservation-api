package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.util.List;
import java.util.UUID;

// HU-COM-020 AC-18.13, AC-20.2, AC-20.3: the open rescheduling episodes of one tenant, and the write of a frozen or resumed one. It reads and writes the rows of that tenant only; called inside the
// transaction of the tenant event.
public interface TenantEpisodeStore {

    List<ReschedulingEpisode> openOf(UUID tenantId);

    void save(ReschedulingEpisode episode);
}
