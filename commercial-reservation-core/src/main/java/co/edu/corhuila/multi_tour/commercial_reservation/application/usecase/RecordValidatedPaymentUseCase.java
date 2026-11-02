package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordValidatedPaymentCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RecordValidatedPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingPaymentDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.util.List;
import java.util.Objects;

// The caller is the saga, trusted by the SERVICE role of its token (checked by the adapter); the permission of the reviewer was
// checked where the human approved. Applying the amount is the same work as an approval, so it is delegated, not copied.
public class RecordValidatedPaymentUseCase implements RecordValidatedPayment {

    private final DecidePaymentProofUseCase decisions;
    private final ReservationRepository reservations;

    public RecordValidatedPaymentUseCase(DecidePaymentProofUseCase decisions, ReservationRepository reservations) {
        this.decisions = decisions;
        this.reservations = reservations;
    }

    @Override
    public DecidedProof record(RecordValidatedPaymentCommand command) {
        if (command.verifiedAmountCents() == null) throw new MissingPaymentDataException(List.of("verifiedAmountCents"));
        Reservation reservation = reservations.findByTenantAndAttempt(command.tenantId(), command.attemptId())
                .filter(found -> found.tenantId().equals(command.tenantId()))
                .orElseThrow(() -> new PaymentProofNotFoundException(command.attemptId()));
        PaymentAttempt attempt = reservation.attempts().stream().filter(found -> found.attemptId().equals(command.attemptId()))
                .findFirst().orElseThrow(() -> new PaymentProofNotFoundException(command.attemptId()));
        if (attempt.validationStatus() == ValidationStatus.APPROVED && sameDecision(attempt, command)) {
            return new DecidedProof(reservation, attempt, attempt.appliedAmount(), attempt.excessAmount());
        }
        return decisions.recordApproval(command.tenantId(), command.approvedBy(), command.attemptId(), command.verifiedAmountCents(),
                command.reason());
    }

    private static boolean sameDecision(PaymentAttempt attempt, RecordValidatedPaymentCommand command) {
        String reason = command.reason() == null || command.reason().isBlank() ? null : command.reason().strip();
        return attempt.verifiedAmount().equals(new Cents(command.verifiedAmountCents())) && Objects.equals(attempt.decisionReason(), reason);
    }
}
