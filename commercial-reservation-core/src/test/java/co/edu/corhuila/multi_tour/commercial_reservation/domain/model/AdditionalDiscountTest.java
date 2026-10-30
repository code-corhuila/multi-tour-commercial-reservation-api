package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// RN-05 / AC-04.6 at the aggregate: minimum 1 %, never below zero. TODO DP-03: no cap, NO DEFINIDO.
class AdditionalDiscountTest {

    @Test void onePercentIsTheMinimumAndTakesTheExactCents() {
        var line = AdditionalDiscount.preview(new Cents(1_000_000), new BasisPoints(100));
        assertThat(line.amount()).isEqualTo(new Cents(10_000));
        assertThat(line.subtotal()).isEqualTo(new Cents(990_000));
    }

    @Test void belowOnePercentIsRefused() {
        assertThatThrownBy(() -> AdditionalDiscount.preview(new Cents(1_000_000), new BasisPoints(99)))
                .isInstanceOf(DomainException.class).extracting("field").isEqualTo("percentage");
    }

    @Test void aNegativeRateCannotExist() {
        assertThatThrownBy(() -> new BasisPoints(-1)).isInstanceOf(DomainException.class);
    }

    @Test void theSubtotalNeverGoesBelowZero() {
        var line = AdditionalDiscount.preview(new Cents(500), new BasisPoints(10_000));
        assertThat(line.subtotal()).isEqualTo(new Cents(0));
    }

    // The table refuses amount_cents = 0, so a discount that takes nothing is a business rule and never a technical error.
    @Test void aPercentageThatRoundsToZeroCentsIsABusinessRuleViolation() {
        assertThatThrownBy(() -> AdditionalDiscount.preview(new Cents(49), new BasisPoints(100)))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("at least one cent");
        assertThatThrownBy(() -> AdditionalDiscount.preview(new Cents(0), new BasisPoints(10_000)))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test void theSmallestSubtotalWhereOnePercentTakesACentIsAccepted() {
        var line = AdditionalDiscount.preview(new Cents(50), new BasisPoints(100));
        assertThat(line.amount()).isEqualTo(new Cents(1));
        assertThat(line.subtotal()).isEqualTo(new Cents(49));
    }

    @Test void aReasonMustNotBeBlank() {
        assertThatThrownBy(() -> AuthorizableReason.create(null, null, " ")).isInstanceOf(DomainException.class);
    }
}
