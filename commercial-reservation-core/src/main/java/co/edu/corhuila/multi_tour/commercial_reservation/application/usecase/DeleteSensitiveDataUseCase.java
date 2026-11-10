package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeletionResult;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeletionResult.Outcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RetentionFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DeleteSensitiveData;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationClosingReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionDeletions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionFactsReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RetentionAnchor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataDeletion;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// HU-COM-019 plan D-04: the deletion of the sensitive data of one reservation, one use case, one transaction. The service rechecks the year at the call (never trusts the
// caller), so a reservation without anchor or not yet due deletes nothing; a second call finds the record and changes nothing. The risk rows of the companions go with their
// own reservation; the risk row of the customer goes only when no other reservation of the customer still waits (RN-05), decided under a lock on the customer.
public class DeleteSensitiveDataUseCase implements DeleteSensitiveData {

    private final RetentionFactsReader facts;
    private final ReservationClosingReader closings;
    private final RetentionDeletions deletions;
    private final RetentionEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public DeleteSensitiveDataUseCase(RetentionFactsReader facts, ReservationClosingReader closings, RetentionDeletions deletions, RetentionEventOutbox outbox,
            PaymentTransaction transaction, Clock clock) {
        this.facts = facts;
        this.closings = closings;
        this.deletions = deletions;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public DeletionResult delete(UUID tenantId, UUID reservationId) {
        RetentionFacts own = facts.factsOf(tenantId, reservationId).orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (deletions.isDeleted(tenantId, reservationId)) return new DeletionResult(Outcome.ALREADY_DELETED, 0, false);
        Instant now = Instant.now(clock);
        Optional<RetentionAnchor> anchor = anchorOf(tenantId, reservationId, own, now);
        if (anchor.isEmpty()) return new DeletionResult(Outcome.NO_ANCHOR, 0, false);
        if (!anchor.get().isDue(now, own.zone())) return new DeletionResult(Outcome.NOT_DUE, 0, false);
        return transaction.inTransaction(() -> {
            deletions.lockCustomer(tenantId, own.customerId());
            int companionRows = deletions.deleteCompanionRiskData(tenantId, reservationId);
            boolean customerData = deletions.otherReservationsOf(tenantId, own.customerId(), reservationId).stream().allMatch(other -> settled(tenantId, other, now));
            if (customerData) deletions.deleteCustomerRiskData(tenantId, own.customerId());
            SensitiveDataDeletion record = new SensitiveDataDeletion(reservationId, tenantId, own.customerId(), anchor.get().at(), anchor.get().basis(), now, customerData, companionRows);
            deletions.record(record);
            outbox.append(record);
            return new DeletionResult(Outcome.DELETED, companionRows, customerData);
        });
    }

    private Optional<RetentionAnchor> anchorOf(UUID tenantId, UUID reservationId, RetentionFacts of, Instant now) {
        return RetentionAnchor.of(of.departure(), of.reservation(), closings.closingOf(tenantId, reservationId), of.zone(), now);
    }

    // RN-05: another reservation no longer holds the customer data back when it was already deleted or has an anchor and its year has passed.
    private boolean settled(UUID tenantId, UUID reservationId, Instant now) {
        if (deletions.isDeleted(tenantId, reservationId)) return true;
        return facts.factsOf(tenantId, reservationId).flatMap(other -> anchorOf(tenantId, reservationId, other, now).map(anchor -> anchor.isDue(now, other.zone()))).orElse(false);
    }
}
