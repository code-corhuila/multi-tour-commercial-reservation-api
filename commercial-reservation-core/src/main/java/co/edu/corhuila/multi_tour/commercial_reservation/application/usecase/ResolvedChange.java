package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;

// AC-12.1: a field that is absent stays as the reservation has it. A change with no field to change, or one that names another tour, is a
// validation error naming the field (the tour is changed with the adapters, not here).
final class ResolvedChange {

    private ResolvedChange() {
    }

    static ModificationRequest of(Reservation reservation, ModificationRequest asked) {
        if (asked.changeType() == null) throw new DomainException("changeType", "changeType is required");
        if (asked.partySize() == null && asked.departureId() == null && asked.companions() == null && asked.serviceId() == null)
            throw new DomainException("changeType", "a change needs at least one field to change");
        if (asked.serviceId() != null && !asked.serviceId().equals(reservation.service().serviceId()))
            throw new DomainException("serviceId", "changing the tour is not supported yet");
        if (asked.changeType() == co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType.DATE_CHANGE
                && (asked.departureId() == null || asked.departureId().equals(reservation.departureId())))
            throw new DomainException("salidaId", "a date change names another departure");
        return new ModificationRequest(asked.changeType(), asked.partySize() == null ? reservation.people() : asked.partySize(),
                asked.departureId() == null ? reservation.departureId() : asked.departureId(),
                asked.companions() == null ? reservation.companions() : asked.companions(), reservation.service().serviceId());
    }
}
