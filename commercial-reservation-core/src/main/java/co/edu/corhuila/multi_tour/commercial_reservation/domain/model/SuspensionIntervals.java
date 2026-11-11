package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// HU-COM-020 RN-04, RN-05; AC-20.5, plan D-03: the periods of inactivity of one tenant, read from the history of suspensions, so a mark computed later (the 24 hours to review a proof, the 24-hour
// financial decisions) leaves them out. An interval open at the end (resumedAt null) is still running. The contingency terms ignore the suspension. Immutable; pure.
public record SuspensionIntervals(List<Interval> intervals) {

    public record Interval(Instant suspendedAt, Instant resumedAt) {
        public Interval {
            if (suspendedAt == null) throw new DomainException("suspendedAt", "the start of the suspension is required");
            if (resumedAt != null && resumedAt.isBefore(suspendedAt)) throw new DomainException("resumedAt", "a suspension cannot end before it starts");
        }
    }

    public SuspensionIntervals {
        intervals = intervals.stream().sorted(Comparator.comparing(Interval::suspendedAt)).toList();
    }

    // The active time (not suspended) inside the window [from, to].
    public Duration activeBetween(Instant from, Instant to) {
        Duration active = Duration.between(from, to);
        for (Interval interval : intervals) {
            Instant start = interval.suspendedAt().isAfter(from) ? interval.suspendedAt() : from;
            Instant end = interval.resumedAt() == null || interval.resumedAt().isAfter(to) ? to : interval.resumedAt();
            if (end.isAfter(start)) active = active.minus(Duration.between(start, end));
        }
        return active;
    }

    // The instant at which the period of active time has elapsed from the start, skipping the suspended time; empty while a suspension that cannot end the period is still open.
    public Optional<Instant> endOf(Instant start, Duration period) {
        Instant cursor = start;
        Duration left = period;
        for (Interval interval : intervals) {
            if (interval.resumedAt() != null && !interval.resumedAt().isAfter(cursor)) continue;
            if (interval.suspendedAt().isAfter(cursor)) {
                Duration free = Duration.between(cursor, interval.suspendedAt());
                if (free.compareTo(left) >= 0) return Optional.of(cursor.plus(left));
                left = left.minus(free);
            }
            if (interval.resumedAt() == null) return Optional.empty();
            cursor = interval.resumedAt();
        }
        return Optional.of(cursor.plus(left));
    }

    // RN-05: the 24 hours to propose a rescheduling and the 5 business days to execute a refund keep running during the inactivation.
    public Instant contingencyEnd(Instant start, Duration period) {
        return start.plus(period);
    }
}
