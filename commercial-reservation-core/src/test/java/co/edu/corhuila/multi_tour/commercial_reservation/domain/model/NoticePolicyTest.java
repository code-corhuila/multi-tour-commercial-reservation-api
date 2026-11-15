package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-015 api T-01, RN-02: the pure policy that says which fact notifies the customer, and with which of the eight codes. No HTTP, no SQL, no mail.
class NoticePolicyTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID RESERVATION = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    private static PaymentValidated validated() {
        return new PaymentValidated(TENANT, RESERVATION, UUID.randomUUID(), new Cents(100_000), PaymentStatus.DEPOSIT_RECEIVED, new Cents(224_000), UUID.randomUUID(), NOW);
    }

    private static ReservationCancelled cancelled(CancellationCause cause) {
        return new ReservationCancelled(TENANT, RESERVATION, cause, 2, NOW);
    }

    @Test
    void noticePolicyMapsEachNotifyingFactToItsType() {
        assertThat(NoticePolicy.of(validated(), true)).contains(NoticeType.PROOF_APPROVED);
        assertThat(NoticePolicy.of(new PaymentRejected(TENANT, RESERVATION, UUID.randomUUID(), new Cents(324_000), UUID.randomUUID(), NOW), true))
                .contains(NoticeType.PROOF_REJECTED);
        assertThat(NoticePolicy.of(new ReservationConfirmed(TENANT, RESERVATION, PaymentStatus.DEPOSIT_RECEIVED, 2, new Cents(324_000), NOW), false))
                .contains(NoticeType.RESERVATION_CONFIRMED);
        assertThat(NoticePolicy.of(cancelled(CancellationCause.PAYMENT_EXPIRY), false)).contains(NoticeType.RESERVATION_CANCELLED_NON_PAYMENT);
        assertThat(NoticePolicy.of(cancelled(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER), false)).contains(NoticeType.DEPARTURE_CANCELLED);
    }

    @Test
    void aRecordedCreditBalanceNotifiesAsCreditBalanceRecorded() {
        assertThat(NoticePolicy.of(new CreditBalanceRecorded(TENANT, RESERVATION, NOW, UUID.randomUUID(), new Cents(50_000), CreditOrigin.PAYMENT_EXCESS, null), false))
                .contains(NoticeType.CREDIT_BALANCE_RECORDED);
        assertThat(NoticePolicy.of(new CreditBalanceRecorded(TENANT, RESERVATION, NOW, UUID.randomUUID(), new Cents(50_000), CreditOrigin.PAYMENT_EXCESS, UUID.randomUUID()), true))
                .contains(NoticeType.CREDIT_BALANCE_RECORDED);
    }

    @Test
    void aCashPaymentRecordedByStaffDoesNotNotify() {
        assertThat(NoticePolicy.of(validated(), false)).isEmpty();
    }

    @Test
    void aCustomerRequestedCancellationDoesNotNotifyAsDepartureCancelled() {
        assertThat(NoticePolicy.of(cancelled(CancellationCause.CUSTOMER_REQUEST), false)).isEqualTo(Optional.empty());
    }

    @Test
    void theOtherPaymentEventsNeverNotify() {
        assertThat(NoticePolicy.of(new CapacityReleased(TENANT, RESERVATION, UUID.randomUUID(), UUID.randomUUID(),
                CapacityReleased.CANCELLED_FOR_NON_PAYMENT, NOW), true)).isEmpty();
    }

    @Test
    void theEightCodesAreTheOnesOfTheDatabase() {
        assertThat(NoticeType.values()).extracting(Enum::name).containsExactlyInAnyOrder("PROOF_APPROVED", "PROOF_REJECTED",
                "RESERVATION_CONFIRMED", "RESERVATION_CANCELLED_NON_PAYMENT", "REFUND_PROCESSED", "CREDIT_BALANCE_RECORDED", "DEPARTURE_CANCELLED",
                "RESCHEDULING_PROPOSED");
    }
}
