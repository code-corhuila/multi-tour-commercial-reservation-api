package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataDeletion;

// HU-COM-019 plan D-06, RN-11: the outbox of the deletion, written in the transaction of the deletion (norm 5.3.11). The event is SensitiveDataDeleted: identifiers, the anchor and
// its basis, a flag and a count, never a name, a document, a contact or a medical value. The retention job acts for the system, so the event has no actor.
public interface RetentionEventOutbox {

    void append(SensitiveDataDeletion deletion);
}
