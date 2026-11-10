package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-05 of HU-COM-018 (-api), domain half: RN-06, RN-09; AC-18.7, AC-18.10, AC-18.11. An episode ends cancelled when no alternative exists (declared only before any proposal exists) or when a clock
// runs out: the 24 hours to propose, the response deadline of the pending proposal, or the 96-hour limit. The expiry applies exactly one outcome or answers that nothing is due. Pure.
class ReschedulingLapseTest {

    private static final Instant CANCELLED_AT = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant START = Instant.parse("2026-12-20T14:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private static ReschedulingEpisode open() {
        return ReschedulingEpisode.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CANCELLED_AT, CANCELLED_AT);
    }

    private static ReschedulingEpisode pending(Instant at) {
        return open().propose(UUID.randomUUID(), UUID.randomUUID(), 2, ADMIN, at, START);
    }

    @Test
    void noAlternativeIsAcceptedOnlyBeforeAProposalExists() {
        Instant now = CANCELLED_AT.plus(Duration.ofHours(3));
        ReschedulingEpisode none = open().declareNoAlternative(now);

        assertThat(none.state()).isEqualTo(EpisodeState.CANCELLED);
        assertThat(none.reason()).isEqualTo(ClosingReason.NO_ALTERNATIVE);
        assertThat(none.closedAt()).isEqualTo(now);
        assertThatThrownBy(() -> pending(CANCELLED_AT.plusSeconds(60)).declareNoAlternative(now)).isInstanceOf(BusinessRuleViolation.class);
        ReschedulingEpisode voided = pending(CANCELLED_AT.plusSeconds(60)).voidPending(CANCELLED_AT.plusSeconds(120));
        assertThatThrownBy(() -> voided.declareNoAlternative(now)).as("a voided proposal is still a proposal").isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> none.declareNoAlternative(now)).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void theLapseOfTheFirstTwentyFourHoursCancelsTheEpisode() {
        ReschedulingEpisode expired = open().expire(CANCELLED_AT.plus(Duration.ofHours(24)));

        assertThat(expired.state()).isEqualTo(EpisodeState.CANCELLED);
        assertThat(expired.reason()).isEqualTo(ClosingReason.PROPOSAL_PERIOD_EXPIRED);
        assertThatThrownBy(() -> open().expire(CANCELLED_AT.plus(Duration.ofHours(24)).minusSeconds(1))).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("nothing is due");
    }

    @Test
    void theResponseDeadlineAndTheEpisodeLimitCancelTheEpisodeAndExpireTheProposal() {
        ReschedulingEpisode proposed = pending(CANCELLED_AT.plus(Duration.ofHours(2)));
        Instant deadline = proposed.pendingProposal().orElseThrow().responseDeadlineAt();

        ReschedulingEpisode late = proposed.expire(deadline);
        assertThat(late.reason()).isEqualTo(ClosingReason.RESPONSE_DEADLINE_EXPIRED);
        assertThat(late.proposals().get(0).state()).isEqualTo(ProposalState.EXPIRED);
        assertThat(late.proposals().get(0).closedAt()).isEqualTo(deadline);
        assertThatThrownBy(() -> proposed.expire(deadline.minusSeconds(1))).isInstanceOf(BusinessRuleViolation.class);

        ReschedulingEpisode substitute = open().propose(UUID.randomUUID(), UUID.randomUUID(), 2, ADMIN, CANCELLED_AT.plusSeconds(60), START).voidPending(CANCELLED_AT.plus(Duration.ofHours(30)))
                .propose(UUID.randomUUID(), UUID.randomUUID(), 2, ADMIN, CANCELLED_AT.plus(Duration.ofHours(40)), START);
        ReschedulingEpisode limit = substitute.expire(CANCELLED_AT.plus(Duration.ofHours(96)));
        assertThat(limit.reason()).as("the deadline of the substitute is the episode remainder: the limit is what ran out").isEqualTo(ClosingReason.EPISODE_LIMIT_REACHED);
        assertThat(limit.proposals().get(1).state()).isEqualTo(ProposalState.EXPIRED);
        assertThat(limit.state()).isEqualTo(EpisodeState.CANCELLED);
    }

    @Test
    void aClosedEpisodeNeverExpires() {
        ReschedulingEpisode closed = open().expire(CANCELLED_AT.plus(Duration.ofHours(30)));

        assertThatThrownBy(() -> closed.expire(CANCELLED_AT.plus(Duration.ofHours(200)))).isInstanceOf(InvalidStatusTransitionException.class);
    }
}
