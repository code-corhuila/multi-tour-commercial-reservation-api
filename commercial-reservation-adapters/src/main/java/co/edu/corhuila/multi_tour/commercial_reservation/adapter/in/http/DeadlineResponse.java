package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import java.util.UUID;

// ReservationPaymentDeadline of the contract (AC-11.6): the stored deadline, the departure start and the effective deadline derived
// from both. The extension fields are present once the single extension was granted.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeadlineResponse(UUID reservationId, String kind, String dueAt, Integer extensionHours, String extensionGrantedAt,
        UUID extensionGrantedBy, String departureStartsAt, String effectiveDeadline, boolean frozen) {

    static DeadlineResponse of(DeadlineView view) {
        return of(view, true);
    }

    // The Final Customer sees that the deadline was extended, never which collaborator granted it.
    static DeadlineResponse of(DeadlineView view, boolean showGrantor) {
        var d = view.deadline();
        return new DeadlineResponse(view.reservationId(), d.kind().name(), d.dueAt().toString(), d.extensionHours(),
                d.extensionGrantedAt() == null ? null : d.extensionGrantedAt().toString(), showGrantor ? d.extensionGrantedBy() : null,
                view.departureStartsAt() == null ? null : view.departureStartsAt().toString(), view.effective().toString(), d.frozen());
    }
}
