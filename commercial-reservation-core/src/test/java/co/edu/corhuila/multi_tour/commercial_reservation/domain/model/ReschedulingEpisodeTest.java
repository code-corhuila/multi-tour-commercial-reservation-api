package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-01 of HU-COM-018 (-api): RN-04; AC-18.5 (plan D-02, D-03). An episode is the incident of one reservation whose departure was cancelled for a cause not attributable to the
// customer. It runs three clocks counted from the cancellation instant, never from the call: 24 hours to propose, the response deadline of each proposal (the lowest of 72 hours
// from its notification, the episode remainder and the proposed start) and the 96-hour limit, which a substitute proposal never restarts. Pure: the clock is an argument.
class ReschedulingEpisodeTest {

    private static final Instant CANCELLED_AT = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant PROPOSED_START = Instant.parse("2026-12-20T14:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private static ReschedulingEpisode episode() {
        return ReschedulingEpisode.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CANCELLED_AT, CANCELLED_AT.plusSeconds(90));
    }

    private static ReschedulingEpisode proposed(ReschedulingEpisode episode, Instant at, Instant start) {
        return episode.propose(UUID.randomUUID(), UUID.randomUUID(), 2, ADMIN, at, start);
    }

    @Test
    void anEpisodeStartsAtTheCancellationInstantWithTheThreeClocks() {
        ReschedulingEpisode episode = episode();

        assertThat(episode.state()).isEqualTo(EpisodeState.OPEN);
        assertThat(episode.cancelledAt()).isEqualTo(CANCELLED_AT);
        assertThat(episode.openedAt()).isEqualTo(CANCELLED_AT.plusSeconds(90));
        assertThat(episode.proposalPeriodEnd()).as("24 hours from the cancellation, not from the call").isEqualTo(CANCELLED_AT.plus(Duration.ofHours(24)));
        assertThat(episode.episodeLimit()).as("96 hours from the original cancellation").isEqualTo(CANCELLED_AT.plus(Duration.ofHours(96)));
        assertThat(episode.proposals()).isEmpty();
        assertThat(episode.pendingProposal()).isEmpty();
        assertThat(episode.closedAt()).isNull();
        assertThat(episode.reason()).isNull();
    }

    @Test
    void theResponseDeadlineIsTheLowestOf72hTheEpisodeRemainderAndTheProposedStart() {
        Instant notified = CANCELLED_AT.plus(Duration.ofHours(10));

        ReschedulingEpisode far = proposed(episode(), notified, PROPOSED_START);
        assertThat(far.pendingProposal().orElseThrow().responseDeadlineAt()).as("72 hours from the notification").isEqualTo(notified.plus(Duration.ofHours(72)));

        ReschedulingEpisode lateInEpisode = proposed(proposed(episode(), CANCELLED_AT.plus(Duration.ofHours(2)), PROPOSED_START).voidPending(CANCELLED_AT.plus(Duration.ofHours(25))), CANCELLED_AT.plus(Duration.ofHours(40)), PROPOSED_START);
        assertThat(lateInEpisode.pendingProposal().orElseThrow().responseDeadlineAt()).as("the episode remainder is lower: 96 hours from the cancellation")
                .isEqualTo(CANCELLED_AT.plus(Duration.ofHours(96)));

        Instant soon = notified.plus(Duration.ofHours(5));
        ReschedulingEpisode startsSoon = proposed(episode(), notified, soon);
        assertThat(startsSoon.pendingProposal().orElseThrow().responseDeadlineAt()).as("the proposed start is lower").isEqualTo(soon);
    }

    @Test
    void substituteProposalsNeverRestartTheEpisodeLimit() {
        Instant first = CANCELLED_AT.plus(Duration.ofHours(10));
        ReschedulingEpisode voided = proposed(episode(), first, PROPOSED_START).voidPending(CANCELLED_AT.plus(Duration.ofHours(30)));

        assertThat(voided.pendingProposal()).isEmpty();
        assertThat(voided.proposals()).singleElement().extracting(ReschedulingProposal::state).isEqualTo(ProposalState.VOID);
        assertThat(voided.episodeLimit()).as("the limit stays at 96 hours from the original cancellation").isEqualTo(CANCELLED_AT.plus(Duration.ofHours(96)));
        assertThat(voided.proposalPeriodEnd()).as("a new period of the lowest of 24 hours and the remainder").isEqualTo(CANCELLED_AT.plus(Duration.ofHours(54)));

        ReschedulingEpisode lateVoid = proposed(episode(), first, PROPOSED_START).voidPending(CANCELLED_AT.plus(Duration.ofHours(90)));
        assertThat(lateVoid.proposalPeriodEnd()).as("the remainder (6 hours) is lower than 24 hours").isEqualTo(CANCELLED_AT.plus(Duration.ofHours(96)));

        ReschedulingEpisode substitute = proposed(voided, CANCELLED_AT.plus(Duration.ofHours(40)), PROPOSED_START);
        assertThat(substitute.episodeLimit()).isEqualTo(CANCELLED_AT.plus(Duration.ofHours(96)));
        assertThat(substitute.proposals()).hasSize(2);
        assertThat(substitute.pendingProposal().orElseThrow().responseDeadlineAt()).isEqualTo(CANCELLED_AT.plus(Duration.ofHours(96)));
    }

    @Test
    void aProposalIsAdmittedOnlyOnceAtATimeAndOnlyInsideThePeriod() {
        ReschedulingEpisode pending = proposed(episode(), CANCELLED_AT.plus(Duration.ofHours(2)), PROPOSED_START);

        assertThatThrownBy(() -> proposed(pending, CANCELLED_AT.plus(Duration.ofHours(3)), PROPOSED_START)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> proposed(episode(), CANCELLED_AT.plus(Duration.ofHours(24)), PROPOSED_START)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> proposed(episode(), CANCELLED_AT.minusSeconds(1), PROPOSED_START)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> episode().propose(UUID.randomUUID(), UUID.randomUUID(), 0, ADMIN, CANCELLED_AT.plusSeconds(60), PROPOSED_START))
                .isInstanceOf(DomainException.class);
    }
}
