package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// T-01 of HU-COM-019 (-api): RN-02 to RN-04; AC-19.1 to AC-19.5, AC-19.10. The year of the sensitive data counts from the real end of the departure, else from its
// estimated end once seven calendar days of the tenant zone have passed without regularization; a cancelled reservation counts from its closing, else from its
// cancellation. Nothing here reads a clock or a table: the instants, the statuses and the zone are arguments.
class RetentionAnchorTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant ESTIMATED_END = Instant.parse("2026-03-10T15:00:00Z");
    private static final Instant REAL_END = Instant.parse("2026-03-10T18:30:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-02-01T14:00:00Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-02-04T14:00:00Z");

    private static DepartureFacts departure(DepartureStatus status, Instant estimatedEnd, Instant finalizedAt) {
        return new DepartureFacts(status, estimatedEnd, finalizedAt);
    }

    private static ReservationFacts live() {
        return new ReservationFacts(ReservationStatus.COMPLETED, null, false);
    }

    private static ReservationFacts cancelled(boolean nothingToTreat) {
        return new ReservationFacts(ReservationStatus.CANCELLED, CANCELLED_AT, nothingToTreat);
    }

    private static Optional<RetentionAnchor> anchor(DepartureFacts departure, ReservationFacts reservation, Optional<Closing> closing, Instant now) {
        return RetentionAnchor.of(departure, reservation, closing, BOGOTA, now);
    }

    @Test
    void theYearCountsFromTheRealEndOfTheDeparture() {
        var found = anchor(departure(DepartureStatus.FINALIZED, ESTIMATED_END, REAL_END), live(), Optional.empty(), REAL_END.plusSeconds(60)).orElseThrow();

        assertThat(found.at()).isEqualTo(REAL_END);
        assertThat(found.basis()).isEqualTo(AnchorBasis.SERVICE_END);
    }

    @Test
    void anUnregularizedDepartureCountsFromItsEstimatedEndAfterSevenCalendarDays() {
        var open = departure(DepartureStatus.IN_EXECUTION, ESTIMATED_END, null);

        assertThat(anchor(open, live(), Optional.empty(), Instant.parse("2026-03-16T12:00:00Z"))).isEmpty();
        var found = anchor(open, live(), Optional.empty(), Instant.parse("2026-03-18T12:00:00Z")).orElseThrow();
        assertThat(found.at()).isEqualTo(ESTIMATED_END);
        assertThat(found.basis()).isEqualTo(AnchorBasis.ESTIMATED_END);
    }

    @Test
    void theSevenDaysAreCountedInTheCalendarOfTheTenantZone() {
        var open = departure(DepartureStatus.IN_EXECUTION, Instant.parse("2026-03-10T03:30:00Z"), null);

        // 2026-03-09 22:30 in Bogota: the seventh calendar day ends at the local midnight of 2026-03-17, that is 2026-03-17T05:00:00Z.
        assertThat(anchor(open, live(), Optional.empty(), Instant.parse("2026-03-17T04:59:59Z"))).isEmpty();
        assertThat(anchor(open, live(), Optional.empty(), Instant.parse("2026-03-17T05:00:00Z"))).isPresent();
    }

    @Test
    void theRealEndPrevailsOverTheEstimatedEnd() {
        var found = anchor(departure(DepartureStatus.FINALIZED, ESTIMATED_END, REAL_END), live(), Optional.empty(), Instant.parse("2026-04-30T12:00:00Z")).orElseThrow();

        assertThat(found.at()).isEqualTo(REAL_END);
        assertThat(found.basis()).isEqualTo(AnchorBasis.SERVICE_END);
    }

    @Test
    void aCancelledReservationCountsFromItsClosingOrItsCancellation() {
        var scheduled = departure(DepartureStatus.CANCELLED, ESTIMATED_END, null);

        var closed = anchor(scheduled, cancelled(false), Optional.of(new Closing(CLOSED_AT, false)), CLOSED_AT.plusSeconds(1)).orElseThrow();
        var nothing = anchor(scheduled, cancelled(true), Optional.empty(), CANCELLED_AT.plusSeconds(1)).orElseThrow();

        assertThat(closed.at()).isEqualTo(CLOSED_AT);
        assertThat(closed.basis()).isEqualTo(AnchorBasis.CLOSING);
        assertThat(nothing.at()).isEqualTo(CANCELLED_AT);
        assertThat(nothing.basis()).isEqualTo(AnchorBasis.CANCELLATION_NO_VALUES);
        assertThat(anchor(scheduled, cancelled(false), Optional.empty(), Instant.parse("2027-06-01T00:00:00Z"))).isEmpty();
    }

    @Test
    void aClosingBlockedByTheInactivationCountsFromTheCancellation() {
        var found = anchor(departure(DepartureStatus.CANCELLED, ESTIMATED_END, null), cancelled(false), Optional.of(new Closing(null, true)),
                CANCELLED_AT.plusSeconds(1)).orElseThrow();

        assertThat(found.at()).isEqualTo(CANCELLED_AT);
        assertThat(found.basis()).isEqualTo(AnchorBasis.CANCELLATION_BLOCKED);
    }

    @Test
    void aReservationWithNoAnchorIsNeverDue() {
        var scheduled = departure(DepartureStatus.SCHEDULED, ESTIMATED_END, null);
        var unknown = departure(DepartureStatus.IN_EXECUTION, null, null);

        assertThat(anchor(scheduled, new ReservationFacts(ReservationStatus.CONFIRMED, null, false), Optional.empty(), Instant.parse("2026-03-01T00:00:00Z"))).isEmpty();
        assertThat(anchor(unknown, live(), Optional.empty(), Instant.parse("2026-03-01T00:00:00Z"))).isEmpty();
        assertThat(anchor(scheduled, new ReservationFacts(ReservationStatus.CANCELLED, null, true), Optional.empty(), Instant.parse("2026-03-01T00:00:00Z"))).isEmpty();
    }

    @Test
    void oneYearMinusOneSecondIsNotDueAndOneYearIs() {
        var found = anchor(departure(DepartureStatus.FINALIZED, ESTIMATED_END, REAL_END), live(), Optional.empty(), REAL_END).orElseThrow();
        Instant due = REAL_END.atZone(BOGOTA).plusYears(1).toInstant();

        assertThat(found.isDue(due.minusSeconds(1), BOGOTA)).isFalse();
        assertThat(found.isDue(due, BOGOTA)).isTrue();
    }
}
