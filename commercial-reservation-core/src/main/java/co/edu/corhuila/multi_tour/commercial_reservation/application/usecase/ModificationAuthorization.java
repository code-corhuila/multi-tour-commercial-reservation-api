package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;

// HU-COM-012 RN-09: only the Administrator and the Collaborator quote and modify, never the Final Customer (RN-M-13); the check runs before any load.
final class ModificationAuthorization {

    private ModificationAuthorization() {
    }

    static void requireWrite(PaymentActor actor) {
        CancellationAuthorization.requireCancel(actor);
    }
}
