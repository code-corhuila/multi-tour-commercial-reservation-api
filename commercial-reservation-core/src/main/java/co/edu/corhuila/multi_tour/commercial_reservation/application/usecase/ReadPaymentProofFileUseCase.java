package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadPaymentProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.UUID;

// D-07: another tenant's file is a missing proof (404, AC-10.17); a user of the same tenant without the right is refused (403).
// The access leaves no audit record (DP-09) and the content is never logged.
public class ReadPaymentProofFileUseCase implements ReadPaymentProofFile {

    private final ReservationRepository reservations;
    private final PaymentProofStore proofs;

    public ReadPaymentProofFileUseCase(ReservationRepository reservations, PaymentProofStore proofs) {
        this.reservations = reservations;
        this.proofs = proofs;
    }

    @Override
    public ProofFile read(PaymentActor actor, UUID attemptId) {
        Reservation reservation = reservations.findByTenantAndAttempt(actor.tenantId(), attemptId)
                .filter(found -> found.tenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
        PaymentProofAuthorization.requireFile(actor, reservation);
        return proofs.read(actor.tenantId(), attemptId).orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
    }
}
