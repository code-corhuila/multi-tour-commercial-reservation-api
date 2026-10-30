package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterTransferCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisteredPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterTransfer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingPaymentDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

// The core of a transfer registration (plan D-06): the body, then the reservation, the departure and the deadline, and
// only then the writes, all in one transaction. The attempt takes the user of the command, never of a body (AC-09.9).
public class RegisterTransferUseCase implements RegisterTransfer {

    private final PaymentPreconditions preconditions;
    private final ReservationRepository reservations;
    private final PaymentProofStore proofs;
    private final PaymentEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final IdGenerator ids;

    public RegisterTransferUseCase(ReservationRepository reservations, DepartureReader departures, PaymentDeadlineReader deadlines,
            EnabledPaymentOptions options, PaymentConditionReader conditions, SalidaCapacityHolder holder, PaymentProofStore proofs,
            PaymentEventOutbox outbox, PaymentTransaction transaction, IdGenerator ids, Clock clock) {
        this.preconditions = new PaymentPreconditions(reservations, departures, deadlines, options, conditions, holder, clock);
        this.reservations = reservations;
        this.proofs = proofs;
        this.outbox = outbox;
        this.transaction = transaction;
        this.ids = ids;
    }

    @Override
    public RegisteredPayment register(RegisterTransferCommand command) {
        List<String> missing = new ArrayList<>();
        if (command.amountCents() == null) missing.add("amountCents");
        if (command.proof() == null) missing.add("proof");
        if (!missing.isEmpty()) throw new MissingPaymentDataException(missing);
        Cents amount = Cents.of("amountCents", command.amountCents());
        ProofFile proof = ProofFile.of(command.proof());
        Reservation reservation = preconditions.requireOpenFor(command.tenantId(), command.reservationId(), PaymentMethod.TRANSFER);
        Cents minimum = preconditions.minimumFor(reservation);
        return transaction.inTransaction(() -> {
            PaymentAttempt attempt = reservation.registerTransfer(ids.newId(), amount, minimum, command.actorId(), preconditions.now());
            preconditions.holdFirstSeat(reservation);
            proofs.save(command.tenantId(), attempt.attemptId(), proof);
            reservations.save(reservation);
            reservation.pullEvents().forEach(outbox::append);
            return new RegisteredPayment(reservation, attempt);
        });
    }
}
