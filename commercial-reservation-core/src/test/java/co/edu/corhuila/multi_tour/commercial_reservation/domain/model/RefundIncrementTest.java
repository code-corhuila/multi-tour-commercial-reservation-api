package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-04 of HU-COM-016 (-api): AC-16.16, DP-11. A late value on a decided refund is one more automatic authorization of the same decision for the
// increment only; a decision that is not a refund cannot be incremented.
class RefundIncrementTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private static FinancialDecision open() {
        return FinancialDecision.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DecisionKind.PENDING_TREATMENT, new Cents(30_000), null, null,
                NOW);
    }

    @Test
    void theIncrementIsAnAutomaticAuthorizationOfTheSameDecision() {
        FinancialDecision decided = open().recordRefundFull(UUID.randomUUID(), NOW);

        RefundAuthorization increment = RefundAuthorization.increment(UUID.randomUUID(), decided, new Cents(4_000), NOW, NOW.plusSeconds(60));

        assertThat(increment.financialDecisionId()).isEqualTo(decided.id());
        assertThat(increment.authorizedAmount()).isEqualTo(new Cents(4_000));
        assertThat(increment.potentialRefundValue()).isEqualTo(new Cents(4_000));
        assertThat(increment.mode()).isEqualTo(AuthorizationMode.AUTOMATIC_DETERMINATION);
        assertThat(increment.authorizedBy()).isNull();
        assertThat(increment.origin()).isEqualTo(RefundOrigin.PENDING_TREATMENT);
    }

    @Test
    void anOpenOrRejectedDecisionCannotBeIncremented() {
        assertThatThrownBy(() -> RefundAuthorization.increment(UUID.randomUUID(), open(), new Cents(1), NOW, NOW))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
