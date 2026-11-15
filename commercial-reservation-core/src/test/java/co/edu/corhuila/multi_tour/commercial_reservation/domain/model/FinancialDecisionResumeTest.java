package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03 of HU-COM-020 (-api): RN-04; AC-20.5, AC-20.7. A 24-hour decision frozen by the inactivation of its tenant resumes with the time it had left: its due instant becomes the instant of the
// resume plus the remaining time (kept in resumedDueAt, because the recognition instant never changes) and it is never defaulted before. A decision that was not frozen does not move. Pure.
class FinancialDecisionResumeTest {

    private static final Instant RECOGNIZED = Instant.parse("2026-11-10T08:00:00Z");

    private static FinancialDecision open() {
        return FinancialDecision.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DecisionKind.CUSTOMER_CANCELLATION, new Cents(100_000), new Cents(20_000), null, RECOGNIZED);
    }

    @Test
    void aResumedDecisionEndsTheInstantOfTheResumePlusWhatRemained() {
        FinancialDecision frozen = open().freeze(RECOGNIZED.plus(Duration.ofHours(3)));
        Instant back = RECOGNIZED.plus(Duration.ofHours(40));

        FinancialDecision resumed = frozen.resumed(back);

        assertThat(resumed.isFrozen()).isFalse();
        assertThat(resumed.remainingAtFreezeMinutes()).isNull();
        assertThat(resumed.dueAt()).isEqualTo(back.plus(Duration.ofHours(21)));
        assertThat(resumed.isDue(back.plus(Duration.ofHours(20)))).isFalse();
        assertThat(resumed.isDue(back.plus(Duration.ofHours(21)))).isTrue();
        assertThat(resumed.recognizedAt()).as("the recognition instant never changes").isEqualTo(RECOGNIZED);
    }

    @Test
    void aDecisionThatWasNotFrozenDoesNotMoveAndADecidedOneIsRefused() {
        FinancialDecision open = open();

        assertThat(open.resumed(RECOGNIZED.plusSeconds(60))).isEqualTo(open);
        assertThat(open.dueAt()).isEqualTo(RECOGNIZED.plus(Duration.ofHours(24)));
        FinancialDecision decided = open.recordRefundFull(UUID.randomUUID(), RECOGNIZED.plusSeconds(120));
        assertThat(decided.resumed(RECOGNIZED.plusSeconds(180))).isEqualTo(decided);
    }

    @Test
    void theShiftedDueInstantSurvivesTheLaterMovesOfTheDecision() {
        FinancialDecision resumed = open().freeze(RECOGNIZED.plus(Duration.ofHours(3))).resumed(RECOGNIZED.plus(Duration.ofHours(40)));

        assertThat(resumed.withLateValue(new Cents(5_000)).dueAt()).isEqualTo(resumed.dueAt());
        assertThat(resumed.freeze(RECOGNIZED.plus(Duration.ofHours(41))).remainingAtFreezeMinutes()).isEqualTo(20 * 60);
        assertThat(resumed.recordRefundFull(UUID.randomUUID(), RECOGNIZED.plus(Duration.ofHours(45))).dueAt()).isEqualTo(resumed.dueAt());
        assertThatThrownBy(() -> resumed.applyDefault(RECOGNIZED.plus(Duration.ofHours(41)))).isInstanceOf(BusinessRuleViolation.class);
    }
}
