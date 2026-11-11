package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadPaymentProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.UUID;

// D-07: another tenant's file is a missing proof (404, AC-10.17); a user of the same tenant without the right is refused (403).
// The owner is the customer linked to the account of the token, because the subject of a Final Customer is the account (AC-10.19).
// The access leaves no audit record (DP-09) and the content is never logged.
public class ReadPaymentProofFileUseCase implements ReadPaymentProofFile {

    private final ReservationRepository reservations;
    private final PaymentProofStore proofs;
    private final CustomerByAccount accounts;

    public ReadPaymentProofFileUseCase(ReservationRepository reservations, PaymentProofStore proofs, CustomerByAccount accounts) {
        this.reservations = reservations;
        this.proofs = proofs;
        this.accounts = accounts;
    }

    @Override
    public ProofFile read(PaymentActor actor, UUID attemptId) {
        Reservation reservation = reservations.findByTenantAndAttempt(actor.tenantId(), attemptId)
                .filter(found -> found.tenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
        if (!owns(actor, reservation)) PaymentProofAuthorization.requireDecide(actor);
        return proofs.read(actor.tenantId(), attemptId).orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
    }

    private boolean owns(PaymentActor actor, Reservation reservation) {
        if (actor.role() != Role.FINAL_CUSTOMER || actor.actorId() == null) return false;
        return accounts.findByTenantAndAccount(actor.tenantId(), actor.actorId())
                .filter(found -> found.tenantId().equals(actor.tenantId())).map(Customer::customerId)
                .filter(reservation.customerId()::equals).isPresent();
    }
}
