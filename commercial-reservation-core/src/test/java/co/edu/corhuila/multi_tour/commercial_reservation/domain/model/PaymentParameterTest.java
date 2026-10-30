package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-08 of HU-COM-009, the domain part: RN-14; AC-09.17. Hours 1 to 720, the minimum deposit 1 to 10 000 basis points for a
// deposit and none for a full payment (the bounds of the db), and a reason that is never blank.
class PaymentParameterTest {

    private static final UUID ADMIN = new UUID(9, 0xe1), SERVICE = new UUID(9, 0xe2);
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private static PaymentParameterValues deposit(int initial, int additional, Integer bp, Integer advance) {
        return new PaymentParameterValues(PaymentType.DEPOSIT, SERVICE, initial, additional, bp, advance, true, false);
    }

    @Test
    void theValuesWithinTheirBoundsAreAccepted() {
        PaymentParameterValues values = deposit(1, 720, 3_000, 24);

        assertThat(values.minimumDepositBp()).isEqualTo(3_000);
        assertThat(new PaymentParameterValues(PaymentType.FULL_PAYMENT, null, 48, 24, null, null, true, true).serviceId()).isNull();
    }

    @Test
    void hoursOutsideOneToSevenHundredTwentyAreRefusedNamingTheField() {
        assertThatThrownBy(() -> deposit(0, 24, 3_000, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("initialWaitHours"));
        assertThatThrownBy(() -> deposit(24, 721, 3_000, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("additionalWaitHours"));
        assertThatThrownBy(() -> deposit(24, 24, 3_000, 0)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("balanceDeadlineAdvanceHours"));
    }

    @Test
    void aDepositNeedsItsMinimumInBasisPointsAndAFullPaymentHasNone() {
        assertThatThrownBy(() -> deposit(24, 24, null, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("minimumDepositBp"));
        assertThatThrownBy(() -> deposit(24, 24, 0, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> deposit(24, 24, 10_001, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new PaymentParameterValues(PaymentType.FULL_PAYMENT, null, 24, 24, 3_000, null, true, true))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("minimumDepositBp"));
    }

    @Test
    void aChangeKeepsItsAuthorItsTimeAndAMandatoryReason() {
        PaymentParameter change = new PaymentParameter(deposit(24, 24, 3_000, null), ADMIN, "  new season  ", NOW);

        assertThat(change.changedBy()).isEqualTo(ADMIN);
        assertThat(change.changeReason()).isEqualTo("new season");
        assertThat(change.changedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> new PaymentParameter(deposit(24, 24, 3_000, null), ADMIN, "   ", NOW)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.field()).isEqualTo("reason"));
        assertThatThrownBy(() -> new PaymentParameter(deposit(24, 24, 3_000, null), ADMIN, "x".repeat(501), NOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new PaymentParameter(deposit(24, 24, 3_000, null), null, "reason", NOW)).isInstanceOf(DomainException.class);
    }
}
