package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// HU-COM-017 RN-04, AC-17.8: one application of a credit balance to a reservation, with the person who did it and whether it was the customer or the staff. Appended,
// never edited. It is not a payment attempt and moves no cash.
public record CreditApplication(UUID id, UUID creditBalanceId, UUID tenantId, UUID targetReservationId, Cents amount, Instant appliedAt, UUID appliedBy, AppliedBy kind) {

    public enum AppliedBy { CUSTOMER, STAFF }

    public CreditApplication {
        if (id == null || creditBalanceId == null || tenantId == null || targetReservationId == null || amount == null || appliedAt == null || appliedBy == null
                || kind == null)
            throw new DomainException("creditApplication", "the application data is incomplete");
        if (amount.amount() <= 0) throw new DomainException("amountCents", "amountCents must be greater than zero");
    }
}
