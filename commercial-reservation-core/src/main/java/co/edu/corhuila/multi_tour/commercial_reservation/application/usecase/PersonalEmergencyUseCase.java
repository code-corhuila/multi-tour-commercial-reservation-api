package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EmergencyOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ApplyPersonalEmergency;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoRefundDueException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PersonalEmergency;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.Optional;

// Spec 024 (CA-028, AB-01, ADR-004 D4): the refund of a personal emergency is determined by the service and the event is marked as received in the same transaction,
// so a failure after the refund and before the mark leaves neither. The registration never cancels the reservation and never changes it (D-04). No tenant status is
// read: a fact of the past cannot be refused (AC-24.10). A reservation that does not exist for the tenant leaves the event unmarked; a cancelled one is marked and ignored.
// One refund per reservation: a second event finds the first. Nothing is logged here: ids, counts and cents only travel in the event and the refund.
public class PersonalEmergencyUseCase implements ApplyPersonalEmergency {

    private final ProcessedEvents processed;
    private final ReservationRepository reservations;
    private final RefundAuthorizationRepository refunds;
    private final DetermineRefund determine;
    private final PaymentTransaction transaction;

    public PersonalEmergencyUseCase(ProcessedEvents processed, ReservationRepository reservations, RefundAuthorizationRepository refunds, DetermineRefund determine,
            PaymentTransaction transaction) {
        this.processed = processed;
        this.reservations = reservations;
        this.refunds = refunds;
        this.determine = determine;
        this.transaction = transaction;
    }

    @Override
    public EmergencyOutcome apply(PersonalEmergency emergency) {
        return transaction.inTransaction(() -> {
            if (processed.wasProcessed(emergency.eventId())) return EmergencyOutcome.DUPLICATE;
            Optional<Reservation> found = reservations.findByTenantAndId(emergency.tenantId(), emergency.reservationId());
            if (found.isEmpty()) return EmergencyOutcome.UNKNOWN_RESERVATION;
            Reservation reservation = found.get();
            EmergencyOutcome outcome;
            if (reservation.reservationStatus() == ReservationStatus.CANCELLED) {
                outcome = EmergencyOutcome.IGNORED_CANCELLED;
            } else if (emergency.affected() > reservation.people()) {
                throw new DomainException("affected", "affected is above the people of the reservation");
            } else if (refunds.findByReservationAndOrigin(emergency.tenantId(), emergency.reservationId(), RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION).isPresent()) {
                outcome = EmergencyOutcome.ALREADY_DETERMINED;
            } else {
                outcome = determined(emergency);
            }
            processed.record(emergency.eventId());
            return outcome;
        });
    }

    private EmergencyOutcome determined(PersonalEmergency emergency) {
        try {
            determine.determine(emergency.tenantId(), emergency.reservationId(), RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, emergency.affected(), emergency.occurredAt());
            return EmergencyOutcome.REFUND_DETERMINED;
        } catch (NoRefundDueException none) {
            return EmergencyOutcome.NO_REFUND;
        }
    }
}
