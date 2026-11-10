package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// T-02 of HU-COM-020 (-api): RN-02, RN-03; AC-20.2 to AC-20.4, AC-20.18. The inactivation of a tenant freezes every open deadline at the instant of the event with the time it had left and the cause
// TENANT_INACTIVE (zero if already reached); the reactivation resumes only the deadlines frozen by that cause: the effective deadline is the instant of the event plus the remaining time, never past
// the start of the departure; a deadline frozen by the cancelled departure is untouched, and one whose departure was cancelled during the inactivation is not resumed but changes its cause. Pure:
// every instant is an argument and nothing reads a clock.
class PaymentDeadlineFreezeTest {

    private static final Instant START = Instant.parse("2026-12-20T14:00:00Z");
    private static final Instant DUE = Instant.parse("2026-11-12T12:00:00Z");
    private static final Instant INACTIVATED = Instant.parse("2026-11-12T08:00:00Z");
    private static final Instant REACTIVATED = Instant.parse("2026-11-20T09:00:00Z");

    private static PaymentDeadline open() {
        return new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE);
    }

    @Test
    void aFreezeKeepsTheRemainingTimeAtTheEventInstant() {
        PaymentDeadline frozen = open().frozen(INACTIVATED, START, FreezeCause.TENANT_INACTIVE);

        assertThat(frozen.frozen()).isTrue();
        assertThat(frozen.frozenAt()).isEqualTo(INACTIVATED);
        assertThat(frozen.remainingAtFreezeMinutes()).isEqualTo(240);
        assertThat(frozen.freezeCause()).isEqualTo(FreezeCause.TENANT_INACTIVE);
        assertThat(frozen.inForce(START)).as("a frozen deadline has no running instant").isNull();
    }

    @Test
    void theExtensionGrantedStaysRecordedAndCountsInTheRemainingTime() {
        PaymentDeadline extended = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE, 2, INACTIVATED.minusSeconds(3600), null, null, null);

        PaymentDeadline frozen = extended.frozen(INACTIVATED, START, FreezeCause.TENANT_INACTIVE);

        assertThat(frozen.remainingAtFreezeMinutes()).isEqualTo(360);
        assertThat(frozen.extensionHours()).isEqualTo(2);
    }

    @Test
    void aDeadlineAlreadyReachedFreezesWithZeroRemaining() {
        PaymentDeadline frozen = open().frozen(DUE.plus(Duration.ofHours(1)), START, FreezeCause.TENANT_INACTIVE);

        assertThat(frozen.remainingAtFreezeMinutes()).isZero();
    }

    @Test
    void aFrozenDeadlineIsNotFrozenAgain() {
        PaymentDeadline frozen = open().frozen(INACTIVATED, START, FreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED);

        assertThat(frozen.frozen(INACTIVATED.plusSeconds(3600), START, FreezeCause.TENANT_INACTIVE)).isEqualTo(frozen);
    }

    @Test
    void aResumeContinuesWithTheRemainingTimeNeverPastTheDepartureStart() {
        PaymentDeadline resumed = open().frozen(INACTIVATED, START, FreezeCause.TENANT_INACTIVE).resumed(REACTIVATED, START, FreezeCause.TENANT_INACTIVE, false);

        assertThat(resumed.frozen()).isFalse();
        assertThat(resumed.freezeCause()).isNull();
        assertThat(resumed.remainingAtFreezeMinutes()).isNull();
        assertThat(resumed.effective(START)).as("the instant of the event plus what remained").isEqualTo(REACTIVATED.plus(Duration.ofMinutes(240)));
        assertThat(resumed.inForce(START)).isEqualTo(REACTIVATED.plus(Duration.ofMinutes(240)));

        Instant soon = REACTIVATED.plus(Duration.ofHours(1));
        assertThat(open().frozen(INACTIVATED, START, FreezeCause.TENANT_INACTIVE).resumed(REACTIVATED, soon, FreezeCause.TENANT_INACTIVE, false).effective(soon)).isEqualTo(soon);
    }

    @Test
    void aResumeKeepsTheExtensionRecorded() {
        PaymentDeadline extended = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE, 2, INACTIVATED.minusSeconds(3600), null, null, null);

        PaymentDeadline resumed = extended.frozen(INACTIVATED, START, FreezeCause.TENANT_INACTIVE).resumed(REACTIVATED, START, FreezeCause.TENANT_INACTIVE, false);

        assertThat(resumed.extensionHours()).isEqualTo(2);
        assertThat(resumed.effective(START)).isEqualTo(REACTIVATED.plus(Duration.ofMinutes(360)));
    }

    @Test
    void aDeadlineFrozenByTheDepartureIsNotResumedByTheTenant() {
        PaymentDeadline byDeparture = open().frozen(INACTIVATED, START, FreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED);

        assertThat(byDeparture.resumed(REACTIVATED, START, FreezeCause.TENANT_INACTIVE, false)).isEqualTo(byDeparture);
        assertThat(open().resumed(REACTIVATED, START, FreezeCause.TENANT_INACTIVE, false)).as("a deadline that is not frozen is untouched").isEqualTo(open());
    }

    @Test
    void aDeadlineWhoseDepartureWasCancelledIsNotResumed() {
        PaymentDeadline frozen = open().frozen(INACTIVATED, START, FreezeCause.TENANT_INACTIVE);

        PaymentDeadline kept = frozen.resumed(REACTIVATED, START, FreezeCause.TENANT_INACTIVE, true);

        assertThat(kept.frozen()).isTrue();
        assertThat(kept.frozenAt()).isEqualTo(INACTIVATED);
        assertThat(kept.remainingAtFreezeMinutes()).isEqualTo(240);
        assertThat(kept.freezeCause()).isEqualTo(FreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED);
    }
}
