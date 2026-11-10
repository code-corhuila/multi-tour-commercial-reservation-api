package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

// HU-COM-019 RN-02 to RN-04; plan D-02: the instant from which the year of the sensitive data counts, with its basis. A pure function of the facts, the closing, the
// zone of the tenant and the instant of the call: it reads no clock and no table. A reservation with no anchor is never due, so the data of a live reservation is
// never deleted (RN-04). The anchor of a reservation of a departure that finished is its real end; one that did not finish counts from the estimated end once seven
// calendar days of the zone have passed; a cancelled one counts from its closing, else from its cancellation when nothing was left to treat or the closing is blocked.
public record RetentionAnchor(Instant at, AnchorBasis basis) {
    private static final int DAYS_WITHOUT_REGULARIZATION = 7;

    public static Optional<RetentionAnchor> of(DepartureFacts departure, ReservationFacts reservation, Optional<Closing> closing, ZoneId zone, Instant now) {
        if (reservation.status() == ReservationStatus.CANCELLED) return cancelled(reservation, closing);
        if (departure.status() == DepartureStatus.FINALIZED && departure.finalizedAt() != null) {
            return Optional.of(new RetentionAnchor(departure.finalizedAt(), AnchorBasis.SERVICE_END));
        }
        if (departure.status() == DepartureStatus.CANCELLED || departure.estimatedEndsAt() == null) return Optional.empty();
        // Seven calendar days of the zone after the day of the estimated end: the anchor exists from the local midnight that ends the seventh.
        ZonedDateTime available = departure.estimatedEndsAt().atZone(zone).toLocalDate().plusDays(DAYS_WITHOUT_REGULARIZATION + 1L).atStartOfDay(zone);
        return now.isBefore(available.toInstant()) ? Optional.empty() : Optional.of(new RetentionAnchor(departure.estimatedEndsAt(), AnchorBasis.ESTIMATED_END));
    }

    private static Optional<RetentionAnchor> cancelled(ReservationFacts reservation, Optional<Closing> closing) {
        if (closing.isPresent() && !closing.get().blockedByInactivation() && closing.get().at() != null) {
            return Optional.of(new RetentionAnchor(closing.get().at(), AnchorBasis.CLOSING));
        }
        if (reservation.cancelledAt() == null) return Optional.empty();
        if (closing.isPresent() && closing.get().blockedByInactivation()) {
            return Optional.of(new RetentionAnchor(reservation.cancelledAt(), AnchorBasis.CANCELLATION_BLOCKED));
        }
        return reservation.nothingToTreat() ? Optional.of(new RetentionAnchor(reservation.cancelledAt(), AnchorBasis.CANCELLATION_NO_VALUES)) : Optional.empty();
    }

    // RN-03: one calendar year of the zone after the anchor, reached at the instant of the call.
    public boolean isDue(Instant now, ZoneId zone) {
        return !now.isBefore(at.atZone(zone).plusYears(1).toInstant());
    }
}
