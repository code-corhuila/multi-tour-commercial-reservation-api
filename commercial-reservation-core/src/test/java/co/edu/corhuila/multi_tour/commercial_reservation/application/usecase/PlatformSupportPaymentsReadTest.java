package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-SEC-007 G-SEC9-1 (Project Lead, 2026-11-11, option A), second group: the read only PLATFORM_SUPPORT actor reads the payments, proofs, deadlines, refunds, credits parameters and the cancellation
// and modification records; every check that registers, quotes, extends, follows up, decides, changes or cancels still refuses it.
class PlatformSupportPaymentsReadTest {
    private static final PaymentActor SUPPORT = new PaymentActor(UUID.randomUUID(), UUID.randomUUID(), PaymentActor.Role.PLATFORM_SUPPORT);

    @Test
    void thePlatformSupportActorReadsPaymentsDeadlinesProofsParametersAndRecords() {
        PaymentAuthorization.requireReadPayments(SUPPORT);
        PaymentAuthorization.requireReadDeadlines(SUPPORT);
        PaymentAuthorization.requireReadParameters(SUPPORT);
        PaymentProofAuthorization.requireRead(SUPPORT);
        CancellationAuthorization.requireRead(SUPPORT);
        RefundAuthorizationRules.requireReader(SUPPORT);
    }

    @Test
    void thePlatformSupportActorReadsThePaymentParametersWithTheirDetail() {
        var repository = new co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParameterRepository() {
            @Override public java.util.List<co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter> findByTenant(UUID tenantId) { return java.util.List.of(); }
            @Override public void save(UUID tenantId, co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter parameter) { }
        };
        var parameters = new ManagePaymentParametersUseCase(repository, null, null, java.time.Clock.systemUTC());

        assertThat(parameters.read(SUPPORT)).isEmpty();
    }

    @Test
    void thePlatformSupportActorNeverRegistersQuotesDecidesChangesOrCancels() {
        assertThatThrownBy(() -> PaymentAuthorization.requireRegister(SUPPORT)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> PaymentAuthorization.requireQuote(SUPPORT)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> PaymentAuthorization.requireExtend(SUPPORT)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> PaymentAuthorization.requireFollowUp(SUPPORT)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> PaymentAuthorization.requireChangeParameters(SUPPORT)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> PaymentProofAuthorization.requireDecide(SUPPORT)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> CancellationAuthorization.requireCancel(SUPPORT)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> RefundAuthorizationRules.requireAdministrator(SUPPORT)).isInstanceOf(PaymentAccessDeniedException.class);
    }
}
