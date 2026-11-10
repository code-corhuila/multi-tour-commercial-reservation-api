package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-05 of HU-COM-018 (-api), domain half: RN-08, RN-09; AC-18.12, AC-18.13. A pending proposal whose proposed departure was cancelled is void (not a rejection, not a lack of answer). With the
// tenant inactive (I1) the episode is cancelled at once with the reason PROPOSED_DEPARTURE_CANCELLED_WHILE_INACTIVE and no new period opens. Pure.
class ReschedulingVoidTest {

    private static final Instant CANCELLED_AT = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant START = Instant.parse("2026-12-20T14:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private static ReschedulingEpisode pending() {
        return ReschedulingEpisode.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CANCELLED_AT, CANCELLED_AT)
                .propose(UUID.randomUUID(), UUID.randomUUID(), 2, ADMIN, CANCELLED_AT.plus(Duration.ofHours(3)), START);
    }

    @Test
    void anInactiveTenantCancelsTheEpisodeAtOnceAndVoidsTheProposalWithNoNewPeriod() {
        Instant now = CANCELLED_AT.plus(Duration.ofHours(30));

        ReschedulingEpisode closed = pending().voidAndCancel(now);

        assertThat(closed.state()).isEqualTo(EpisodeState.CANCELLED);
        assertThat(closed.reason()).isEqualTo(ClosingReason.PROPOSED_DEPARTURE_CANCELLED_WHILE_INACTIVE);
        assertThat(closed.closedAt()).isEqualTo(now);
        assertThat(closed.proposals()).singleElement().satisfies(p -> {
            assertThat(p.state()).isEqualTo(ProposalState.VOID);
            assertThat(p.closedAt()).isEqualTo(now);
            assertThat(p.answer()).isNull();
        });
    }

    @Test
    void onlyAnEpisodeWithAPendingProposalCanBeVoidedAndCancelled() {
        ReschedulingEpisode none = ReschedulingEpisode.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CANCELLED_AT, CANCELLED_AT);
        assertThatThrownBy(() -> none.voidAndCancel(CANCELLED_AT.plusSeconds(60))).isInstanceOf(InvalidStatusTransitionException.class);
        ReschedulingEpisode closed = pending().voidAndCancel(CANCELLED_AT.plus(Duration.ofHours(5)));
        assertThatThrownBy(() -> closed.voidAndCancel(CANCELLED_AT.plus(Duration.ofHours(6)))).isInstanceOf(InvalidStatusTransitionException.class);
    }
}
