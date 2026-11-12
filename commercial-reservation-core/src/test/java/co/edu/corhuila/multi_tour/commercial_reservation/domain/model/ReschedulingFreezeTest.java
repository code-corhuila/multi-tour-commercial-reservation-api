package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-020 (-api), AC-18.13 (2), AC-20.2, AC-20.3, AC-20.6: with the tenant inactive and a proposal pending, the response deadline and the episode remainder freeze for exactly the same time and
// resume with what remained; with no proposal pending nothing freezes (the 24 hours and the episode keep running). An outside answer of the Administrator takes effect while frozen. The minutes the
// episode spent frozen are summed, because the start of the episode never changes and the limit of 96 hours moves by that total. Pure.
class ReschedulingFreezeTest {

    private static final Instant CANCELLED_AT = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant NOTIFIED = CANCELLED_AT.plus(Duration.ofHours(2));
    private static final Instant START = Instant.parse("2026-12-20T14:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private static ReschedulingEpisode open() {
        return ReschedulingEpisode.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CANCELLED_AT, CANCELLED_AT);
    }

    private static ReschedulingEpisode pending() {
        return open().propose(UUID.randomUUID(), UUID.randomUUID(), 2, ADMIN, NOTIFIED, START);
    }

    @Test
    void noProposalWithAnInactiveTenantKeepsTheClocksRunning() {
        ReschedulingEpisode none = open();

        ReschedulingEpisode same = none.freeze(CANCELLED_AT.plus(Duration.ofHours(1)));

        assertThat(same).isEqualTo(none);
        assertThat(same.isFrozen()).isFalse();
        assertThat(same.expire(CANCELLED_AT.plus(Duration.ofHours(24))).reason()).isEqualTo(ClosingReason.PROPOSAL_PERIOD_EXPIRED);
    }

    @Test
    void aPendingProposalFreezesBothClocksAndResumesWhatRemained() {
        ReschedulingEpisode proposed = pending();
        Instant deadline = proposed.pendingProposal().orElseThrow().responseDeadlineAt();
        Instant inactivated = NOTIFIED.plus(Duration.ofHours(10));
        Instant limit = proposed.episodeLimit();

        ReschedulingEpisode frozen = proposed.freeze(inactivated);

        assertThat(frozen.isFrozen()).isTrue();
        assertThat(frozen.frozenAt()).isEqualTo(inactivated);
        assertThat(frozen.remainingEpisodeMinutes()).isEqualTo(Duration.between(inactivated, limit).toMinutes());
        assertThat(frozen.pendingProposal().orElseThrow().remainingResponseMinutes()).isEqualTo(Duration.between(inactivated, deadline).toMinutes());

        Instant back = inactivated.plus(Duration.ofHours(30));
        ReschedulingEpisode resumed = frozen.resumed(back);

        assertThat(resumed.isFrozen()).isFalse();
        assertThat(resumed.remainingEpisodeMinutes()).isNull();
        assertThat(resumed.frozen()).isEqualTo(Duration.ofHours(30));
        assertThat(resumed.episodeLimit()).isEqualTo(limit.plus(Duration.ofHours(30)));
        assertThat(resumed.pendingProposal().orElseThrow().responseDeadlineAt()).isEqualTo(deadline.plus(Duration.ofHours(30)));
        assertThat(resumed.pendingProposal().orElseThrow().remainingResponseMinutes()).isNull();
    }

    @Test
    void aFrozenEpisodeIsNotDueUntilItResumesAndTheFrozenTotalAddsUpAcrossFreezes() {
        ReschedulingEpisode proposed = pending();
        Instant deadline = proposed.pendingProposal().orElseThrow().responseDeadlineAt();
        ReschedulingEpisode frozen = proposed.freeze(NOTIFIED.plus(Duration.ofHours(1)));

        assertThatThrownBy(() -> frozen.expire(deadline.plus(Duration.ofDays(10)))).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("nothing is due");

        ReschedulingEpisode once = frozen.resumed(NOTIFIED.plus(Duration.ofHours(11)));
        ReschedulingEpisode twice = once.freeze(NOTIFIED.plus(Duration.ofHours(12))).resumed(NOTIFIED.plus(Duration.ofHours(17)));

        assertThat(once.frozen()).isEqualTo(Duration.ofHours(10));
        assertThat(twice.frozen()).isEqualTo(Duration.ofHours(15));
        assertThat(twice.episodeLimit()).isEqualTo(CANCELLED_AT.plus(ReschedulingDeadlines.EPISODE_LIMIT).plus(Duration.ofHours(15)));
        assertThat(twice.freeze(NOTIFIED.plus(Duration.ofHours(18))).freeze(NOTIFIED.plus(Duration.ofHours(19))).frozenAt()).as("a frozen episode is not frozen again")
                .isEqualTo(NOTIFIED.plus(Duration.ofHours(18)));
        assertThat(once.resumed(NOTIFIED.plus(Duration.ofHours(40)))).as("a resume with nothing frozen does nothing").isEqualTo(once);
    }

    @Test
    void anOutsideAnswerOfTheAdministratorTakesEffectWhileFrozen() {
        ReschedulingEpisode frozen = pending().freeze(NOTIFIED.plus(Duration.ofHours(10)));
        Instant now = NOTIFIED.plus(Duration.ofDays(8));
        Instant given = NOTIFIED.plus(Duration.ofHours(9));

        ReschedulingEpisode accepted = frozen.answer(ProposalAnswer.outside(true, ADMIN, "PHONE", "agreed by phone", given), now);

        assertThat(accepted.state()).isEqualTo(EpisodeState.RESCHEDULED);
        assertThat(accepted.reason()).isEqualTo(ClosingReason.ACCEPTED);
        assertThat(accepted.proposals().get(0).state()).isEqualTo(ProposalState.ACCEPTED);
        assertThat(accepted.proposals().get(0).remainingResponseMinutes()).isNull();
        assertThat(accepted.isFrozen()).as("answering resumes the clocks first, so no freeze is left on a closed episode").isFalse();
    }

    @Test
    void aClosedEpisodeNeverFreezes() {
        ReschedulingEpisode closed = open().declareNoAlternative(CANCELLED_AT.plusSeconds(60));

        assertThat(closed.freeze(CANCELLED_AT.plusSeconds(120))).isEqualTo(closed);
    }
}
