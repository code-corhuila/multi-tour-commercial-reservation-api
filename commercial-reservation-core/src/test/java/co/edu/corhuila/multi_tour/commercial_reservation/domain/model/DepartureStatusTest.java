package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus.CANCELLED;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus.FINALIZED;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus.IN_EXECUTION;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus.SCHEDULED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// HU-COM-006 AC-06.16: the forward-only matrix (PROPOSED), SCHEDULED -> IN_EXECUTION -> FINALIZED | CANCELLED.
class DepartureStatusTest {

    @Test
    void aStatusMovesOnlyForward() {
        assertThat(SCHEDULED.canMoveTo(IN_EXECUTION)).isTrue();
        assertThat(SCHEDULED.canMoveTo(CANCELLED)).isTrue();
        assertThat(SCHEDULED.canMoveTo(FINALIZED)).isTrue();
        assertThat(IN_EXECUTION.canMoveTo(FINALIZED)).isTrue();
        assertThat(IN_EXECUTION.canMoveTo(CANCELLED)).isTrue();
        assertThat(IN_EXECUTION.canMoveTo(SCHEDULED)).isFalse();
    }

    @Test
    void aFinalStatusNeverChangesAndARepeatIsNotAMove() {
        for (DepartureStatus next : DepartureStatus.values()) {
            assertThat(FINALIZED.canMoveTo(next)).isFalse();
            assertThat(CANCELLED.canMoveTo(next)).isFalse();
            assertThat(next.canMoveTo(next)).isFalse();
        }
    }
}
