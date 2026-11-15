package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-01 of HU-COM-017 (-api): RN-01, RN-09; AC-17.1, AC-17.9, AC-17.12. A credit balance is the customer's, inside the tenant: it is born from a decision that
// ended as the 100 % credit or from the excess of a validated payment, it is never a refund, its available value is derived, and it never expires. Pure.
class CreditBalanceTest {

    private static final Instant AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private static FinancialDecision cancellation() {
        return FinancialDecision.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DecisionKind.CUSTOMER_CANCELLATION,
                new Cents(80_000), new Cents(20_000), null, AT);
    }

    private static CreditBalance credit() {
        return CreditBalance.ofDecision(UUID.randomUUID(), cancellation().recordCreditFull(ADMIN, AT.plusSeconds(60)), ADMIN, AT.plusSeconds(60));
    }

    @Test
    void aCreditBalanceOfADecisionIsOneHundredPercentOfItsBaseAndCreatesNoRefund() {
        FinancialDecision decided = cancellation().recordCreditFull(ADMIN, AT.plusSeconds(60));
        CreditBalance credit = CreditBalance.ofDecision(UUID.randomUUID(), decided, ADMIN, AT.plusSeconds(60));

        assertThat(decided.outcome()).isEqualTo(DecisionOutcome.CREDIT_FULL);
        assertThat(credit.amount()).isEqualTo(decided.baseAmount());
        assertThat(credit.origin()).isEqualTo(CreditOrigin.CUSTOMER_CANCELLATION);
        assertThat(credit.sourceDecisionId()).isEqualTo(decided.id());
        assertThat(credit.sourceAttemptId()).isNull();
        assertThat(credit.sourceReservationId()).isEqualTo(decided.reservationId());
        assertThat(credit.tenantId()).isEqualTo(decided.tenantId());
        assertThat(credit.available()).isEqualTo(new Cents(80_000));
        assertThat(Arrays.stream(CreditBalance.class.getDeclaredMethods()).map(Method::getName)).noneMatch(name -> name.toLowerCase().contains("refund"));
    }

    @Test
    void onlyADecisionThatEndedAsTheCreditCreatesOne() {
        assertThatThrownBy(() -> CreditBalance.ofDecision(UUID.randomUUID(), cancellation(), ADMIN, AT)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> CreditBalance.ofDecision(UUID.randomUUID(), cancellation().recordRefundFull(ADMIN, AT), ADMIN, AT))
                .isInstanceOf(BusinessRuleViolation.class);
        FinancialDecision request = FinancialDecision.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DecisionKind.CREDIT_BALANCE_REFUND_REQUEST,
                new Cents(10_000), null, UUID.randomUUID(), AT);
        assertThatThrownBy(() -> request.recordCreditFull(ADMIN, AT)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void theExcessOfAValidatedPaymentIsACreditOfThatAttemptAndOfNothingElse() {
        UUID tenant = UUID.randomUUID(), reservation = UUID.randomUUID(), attempt = UUID.randomUUID();
        CreditBalance credit = CreditBalance.ofExcess(UUID.randomUUID(), tenant, reservation, attempt, new Cents(15_000), ADMIN, AT);

        assertThat(credit.origin()).isEqualTo(CreditOrigin.PAYMENT_EXCESS);
        assertThat(credit.sourceAttemptId()).isEqualTo(attempt);
        assertThat(credit.sourceDecisionId()).isNull();
        assertThat(credit.available()).isEqualTo(new Cents(15_000));
        assertThatThrownBy(() -> CreditBalance.ofExcess(UUID.randomUUID(), tenant, reservation, attempt, new Cents(0), ADMIN, AT))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void availableIsRecordedMinusAppliedMinusSetAside() {
        CreditBalance credit = credit().withApplied(new Cents(30_000)).withSetAside(new Cents(10_000));

        assertThat(credit.amount()).isEqualTo(new Cents(80_000));
        assertThat(credit.available()).isEqualTo(new Cents(40_000));
        assertThatThrownBy(() -> credit.withApplied(new Cents(50_000))).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> credit.withSetAside(new Cents(50_000))).isInstanceOf(BusinessRuleViolation.class);
        assertThat(credit.withApplied(new Cents(40_000)).available()).isEqualTo(new Cents(0));
    }

    @Test
    void aCreditBalanceNeverExpires() {
        assertThat(Arrays.stream(CreditBalance.class.getRecordComponents()).map(component -> component.getName().toLowerCase()))
                .noneMatch(name -> name.contains("expir") || name.contains("validuntil") || name.contains("deadline"));
        assertThat(Arrays.stream(CreditBalance.class.getDeclaredMethods()).map(Method::getName)).noneMatch(name -> name.toLowerCase().contains("expir"));
        assertThat(credit().available()).isEqualTo(new Cents(80_000));
    }
}
