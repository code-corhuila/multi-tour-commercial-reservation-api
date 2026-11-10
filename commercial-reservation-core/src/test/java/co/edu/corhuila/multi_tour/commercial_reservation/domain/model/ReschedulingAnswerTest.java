package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-04 of HU-COM-018 (-api): RN-05; AC-18.1, AC-18.6, AC-18.14. Accept or reject is the whole answer, once. The customer answers with nothing else; an answer from an outside channel carries
// its channel, its evidence (1 to 500 characters after trimming) and the instant it was given, which cannot be in the future nor before the notification. A second answer, a late one and an
// answer to a void proposal are refused. Accepting closes the episode as RESCHEDULED with the reason ACCEPTED; rejecting closes it as CANCELLED with REJECTED_BY_CUSTOMER. Pure.
class ReschedulingAnswerTest {

    private static final Instant CANCELLED_AT = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant NOTIFIED = CANCELLED_AT.plus(Duration.ofHours(2));
    private static final Instant START = Instant.parse("2026-12-20T14:00:00Z");
    private static final UUID CUSTOMER = UUID.randomUUID(), ADMIN = UUID.randomUUID();

    private static ReschedulingEpisode pending() {
        return ReschedulingEpisode.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CANCELLED_AT, CANCELLED_AT)
                .propose(UUID.randomUUID(), UUID.randomUUID(), 2, ADMIN, NOTIFIED, START);
    }

    @Test
    void theCustomerAcceptsOnceAndTheEpisodeClosesAsRescheduled() {
        Instant now = NOTIFIED.plus(Duration.ofHours(5));
        ReschedulingEpisode accepted = pending().answer(ProposalAnswer.byCustomer(true, CUSTOMER), now);

        assertThat(accepted.state()).isEqualTo(EpisodeState.RESCHEDULED);
        assertThat(accepted.reason()).isEqualTo(ClosingReason.ACCEPTED);
        assertThat(accepted.closedAt()).isEqualTo(now);
        ReschedulingProposal proposal = accepted.proposals().get(0);
        assertThat(proposal.state()).isEqualTo(ProposalState.ACCEPTED);
        assertThat(proposal.answer().source()).isEqualTo(ProposalAnswer.Source.CUSTOMER);
        assertThat(proposal.answer().channel()).isNull();
        assertThat(proposal.answer().evidence()).isNull();
        assertThat(proposal.answer().answeredAt()).isEqualTo(now);
        assertThat(proposal.answer().recordedBy()).isEqualTo(CUSTOMER);
        assertThat(proposal.closedAt()).isEqualTo(now);
    }

    @Test
    void rejectingClosesTheEpisodeAsCancelledForTheCustomerReason() {
        ReschedulingEpisode rejected = pending().answer(ProposalAnswer.byCustomer(false, CUSTOMER), NOTIFIED.plusSeconds(60));

        assertThat(rejected.state()).isEqualTo(EpisodeState.CANCELLED);
        assertThat(rejected.reason()).isEqualTo(ClosingReason.REJECTED_BY_CUSTOMER);
        assertThat(rejected.proposals().get(0).state()).isEqualTo(ProposalState.REJECTED);
    }

    @Test
    void anOutsideAnswerNeedsChannelEvidenceAndAValidInstant() {
        Instant now = NOTIFIED.plus(Duration.ofHours(6));
        ReschedulingEpisode accepted = pending().answer(ProposalAnswer.outside(true, ADMIN, "PHONE", "  The customer called the agency and agreed  ", now.minusSeconds(600)), now);

        assertThat(accepted.proposals().get(0).answer().channel()).isEqualTo("PHONE");
        assertThat(accepted.proposals().get(0).answer().evidence()).as("trimmed").isEqualTo("The customer called the agency and agreed");
        assertThat(accepted.proposals().get(0).answer().answeredAt()).isEqualTo(now.minusSeconds(600));
        assertThat(accepted.proposals().get(0).answer().recordedBy()).isEqualTo(ADMIN);

        ReschedulingEpisode episode = pending();
        assertThatThrownBy(() -> ProposalAnswer.outside(true, ADMIN, "PIGEON", "evidence", now)).isInstanceOf(DomainException.class).extracting("field").isEqualTo("channel");
        assertThatThrownBy(() -> ProposalAnswer.outside(true, ADMIN, "PHONE", "   ", now)).isInstanceOf(DomainException.class).extracting("field").isEqualTo("evidence");
        assertThatThrownBy(() -> ProposalAnswer.outside(true, ADMIN, "PHONE", "x".repeat(501), now)).isInstanceOf(DomainException.class).extracting("field").isEqualTo("evidence");
        assertThatThrownBy(() -> episode.answer(ProposalAnswer.outside(true, ADMIN, "EMAIL", "mail", now.plusSeconds(1)), now)).isInstanceOf(DomainException.class)
                .extracting("field").isEqualTo("answeredAt");
        assertThatThrownBy(() -> episode.answer(ProposalAnswer.outside(true, ADMIN, "EMAIL", "mail", NOTIFIED.minusSeconds(1)), now)).isInstanceOf(DomainException.class)
                .extracting("field").isEqualTo("answeredAt");
    }

    @Test
    void aSecondAnswerIsRefusedAndALateOneSaysTheExpiryApplies() {
        ReschedulingEpisode answered = pending().answer(ProposalAnswer.byCustomer(true, CUSTOMER), NOTIFIED.plusSeconds(60));
        assertThatThrownBy(() -> answered.answer(ProposalAnswer.byCustomer(false, CUSTOMER), NOTIFIED.plusSeconds(120))).isInstanceOf(InvalidStatusTransitionException.class);

        ReschedulingEpisode voided = pending().voidPending(NOTIFIED.plusSeconds(10));
        assertThatThrownBy(() -> voided.answer(ProposalAnswer.byCustomer(true, CUSTOMER), NOTIFIED.plusSeconds(20))).isInstanceOf(InvalidStatusTransitionException.class);

        ReschedulingEpisode episode = pending();
        Instant deadline = episode.pendingProposal().orElseThrow().responseDeadlineAt();
        assertThatThrownBy(() -> episode.answer(ProposalAnswer.byCustomer(true, CUSTOMER), deadline)).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("expiry");
        assertThat(episode.answer(ProposalAnswer.byCustomer(true, CUSTOMER), deadline.minusSeconds(1)).state()).isEqualTo(EpisodeState.RESCHEDULED);
    }
}
