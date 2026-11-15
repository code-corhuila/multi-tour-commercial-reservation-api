package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

// T-01 of HU-COM-019 (-api): AC-19.4, AC-19.5; DD-2. The definitive closing of a cancelled reservation is derived from the settlement of its decisions.
class ClosingTest {
    private static final Instant FIRST = Instant.parse("2026-02-04T14:00:00Z");
    private static final Instant LAST = Instant.parse("2026-02-09T10:00:00Z");

    @Test
    void aReservationWithNoDecisionHasNoClosing() {
        assertThat(Closing.of(List.of())).isEmpty();
    }

    @Test
    void aReservationWithAllItsDecisionsSettledClosesAtTheLatestSettlement() {
        assertThat(Closing.of(List.of(new DecisionSettlement(LAST, false), new DecisionSettlement(FIRST, false))))
                .contains(new Closing(LAST, false));
    }

    @Test
    void aReservationWithAnOpenDecisionIsNotClosed() {
        assertThat(Closing.of(List.of(new DecisionSettlement(FIRST, false), new DecisionSettlement(null, false)))).isEmpty();
    }

    @Test
    void anOpenDecisionFrozenByTheInactivationBlocksTheClosing() {
        assertThat(Closing.of(List.of(new DecisionSettlement(FIRST, false), new DecisionSettlement(null, true)))).contains(new Closing(null, true));
    }
}
