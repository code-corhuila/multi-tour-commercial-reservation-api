package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;

// The actor of the payment use cases is only the verified token: tenant, user, role and permissions (AC-09.9).
final class PaymentActors {
    private PaymentActors() {}

    static PaymentActor of(AuthenticatedPrincipal principal) {
        var customer = CustomerHttpAccess.actor(principal);
        return new PaymentActor(customer.tenantId(), customer.actorId(), PaymentActor.Role.valueOf(customer.role().name()), customer.permissions());
    }
}
