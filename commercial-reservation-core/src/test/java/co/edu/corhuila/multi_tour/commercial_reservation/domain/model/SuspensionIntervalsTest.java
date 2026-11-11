package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// T-03 of HU-COM-020 (-api): RN-04, RN-05; AC-20.5. The review of a proof and the 24-hour decisions are marks, not stored deadlines: the time the tenant was suspended is not counted in them. The
// intervals may be open at the end (still inactive); a window with no overlap keeps its own length. The contingency terms (24 hours to propose, 5 business days to execute a refund) ignore the
// suspension. Pure value object.
class SuspensionIntervalsTest {

    private static final Instant START = Instant.parse("2026-11-10T08:00:00Z");

    private static Instant at(long hours) {
        return START.plus(Duration.ofHours(hours));
    }

    @Test
    void suspendedTimeIsNotCountedInAReviewPeriod() {
        SuspensionIntervals intervals = new SuspensionIntervals(List.of(new SuspensionIntervals.Interval(at(10), at(40))));

        assertThat(intervals.activeBetween(START, at(54))).isEqualTo(Duration.ofHours(24));
        assertThat(intervals.endOf(START, Duration.ofHours(24))).as("10 hours before the inactivation and 30 of inactivity leave 14 hours after the reactivation")
                .isEqualTo(Optional.of(at(54)));
    }

    @Test
    void aWindowWithNoOverlapKeepsItsLengthAndSeveralIntervalsAdd() {
        SuspensionIntervals none = new SuspensionIntervals(List.of(new SuspensionIntervals.Interval(at(100), at(110))));
        assertThat(none.activeBetween(START, at(24))).isEqualTo(Duration.ofHours(24));
        assertThat(none.endOf(START, Duration.ofHours(24))).isEqualTo(Optional.of(at(24)));

        SuspensionIntervals two = new SuspensionIntervals(List.of(new SuspensionIntervals.Interval(at(2), at(4)), new SuspensionIntervals.Interval(at(10), at(15))));
        assertThat(two.endOf(START, Duration.ofHours(24))).isEqualTo(Optional.of(at(31)));
        assertThat(two.activeBetween(START, at(31))).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void anOpenIntervalSuspendsUntilTheEndOfTheWindowAndTheMarkHasNoEnd() {
        SuspensionIntervals open = new SuspensionIntervals(List.of(new SuspensionIntervals.Interval(at(10), null)));

        assertThat(open.activeBetween(START, at(50))).isEqualTo(Duration.ofHours(10));
        assertThat(open.endOf(START, Duration.ofHours(24))).as("while it is inactive the mark does not end").isEmpty();
        assertThat(open.endOf(START, Duration.ofHours(8))).isEqualTo(Optional.of(at(8)));
    }

    @Test
    void theContingencyTermsIgnoreTheSuspension() {
        SuspensionIntervals intervals = new SuspensionIntervals(List.of(new SuspensionIntervals.Interval(at(2), at(30))));

        assertThat(intervals.contingencyEnd(START, Duration.ofHours(24))).isEqualTo(at(24));
    }

    @Test
    void anIntervalThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> new SuspensionIntervals.Interval(at(5), at(4))).isInstanceOf(DomainException.class);
    }
}
