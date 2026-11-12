package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CreateReservationRequest.CompanionRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingReservationDataException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// The body of the quote and of the registration of a modification (HU-COM-012 plan section 2). The user, the tenant, the values and the state
// are never read from it: the strict mapper refuses them. A field that is absent stays as the reservation has it.
public record ModificationHttpRequest(String changeType, Integer people, UUID serviceId, UUID salidaId, List<CompanionRequest> companions, String reason) {

    static final int MAXIMUM_REASON = 500;

    // Every invalid field is named at once (AC-12.1, AC-12.2): the change type always, the reason only when it is required.
    ModificationRequest toRequest(boolean reasonRequired, LocalDate today) {
        var invalid = new ArrayList<String>();
        ChangeType type = null;
        try {
            type = changeType == null ? null : ChangeType.valueOf(changeType);
        } catch (IllegalArgumentException unknown) {
            // named below
        }
        if (type == null) invalid.add("changeType");
        if (reasonRequired && (reason == null || reason.isBlank() || reason.strip().length() > MAXIMUM_REASON)) invalid.add("reason");
        if (people != null && people < 1) invalid.add("people");
        if (!invalid.isEmpty()) throw new MissingReservationDataException(invalid);
        List<Companion> group = null;
        if (companions != null) {
            group = new ArrayList<>();
            for (int i = 0; i < companions.size(); i++) {
                var item = companions.get(i) == null ? new CompanionRequest(null, null, null, null, null, null) : companions.get(i);
                group.add(item.toCompanion(i, today));
            }
        }
        return new ModificationRequest(type, people, salidaId, group, serviceId);
    }
}
