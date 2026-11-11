package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Duration;
import java.time.Instant;

// HU-COM-018 plan D-03, RN-04, AC-18.5: the three clocks of a rescheduling, one pure function each so the reads, the checks and the expiry never drift. The cancellation
// instant comes from the saga, never from the call. The frozen time is what a pending proposal spent frozen by the inactivation of the tenant (it moves the limit).
public final class ReschedulingDeadlines {

    public static final Duration PROPOSAL_PERIOD = Duration.ofHours(24);
    public static final Duration RESPONSE_PERIOD = Duration.ofHours(72);
    public static final Duration EPISODE_LIMIT = Duration.ofHours(96);

    private ReschedulingDeadlines() {
    }

    // 96 hours from the original cancellation, plus the time a pending proposal was frozen; a substitute proposal never restarts it.
    public static Instant episodeLimit(Instant cancelledAt, Duration frozen) {
        return cancelledAt.plus(EPISODE_LIMIT).plus(frozen);
    }

    // 24 hours from the start of the period (the cancellation, or the voiding of a proposal), never past the episode limit.
    public static Instant proposalPeriodEnd(Instant periodStart, Instant episodeLimit) {
        return min(periodStart.plus(PROPOSAL_PERIOD), episodeLimit);
    }

    // The lowest of 72 hours from the notification, the episode remainder and the start of the proposed departure.
    public static Instant responseDeadline(Instant notifiedAt, Instant episodeLimit, Instant proposedStart) {
        return min(min(notifiedAt.plus(RESPONSE_PERIOD), episodeLimit), proposedStart);
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
